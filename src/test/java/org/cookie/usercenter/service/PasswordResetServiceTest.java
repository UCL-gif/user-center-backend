package org.cookie.usercenter.service;

import jakarta.annotation.Resource;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.request.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetServiceTest {
    @Resource PasswordResetService resets;
    @Resource UserService users;
    @Resource AuthSessionService sessions;
    @MockitoBean EmailService emails;
    @MockitoBean AuthChallengeStore challenges;

    private long create() {
        UserAddRequest input = new UserAddRequest();
        input.setUserAccount("resetuser");
        input.setUserPassword("Before2026!");
        input.setEmail("reset@example.com");
        return users.addUser(input);
    }

    private UserLoginRequest login(String password) {
        UserLoginRequest input = new UserLoginRequest();
        input.setUserAccount("resetuser"); input.setUserPassword(password);
        return input;
    }

    private PasswordResetRequest reset(String confirmation) {
        PasswordResetRequest input = new PasswordResetRequest();
        input.setResetToken("a".repeat(43));
        input.setNewPassword("After2026!"); input.setCheckPassword(confirmation);
        return input;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void interfaceInvocationStartsAndClosesTheResetTransaction() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "通过接口调用实现类时，密码重置必须处于事务中");
            return null;
        }).when(challenges).consumeResetToken("a".repeat(43));
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset("After2026!")));
        verify(challenges).consumeResetToken("a".repeat(43));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void resetChangesOnlyPasswordAndOldLoginBecomesInvalid() {
        long id = create();
        var oldRequest = new MockHttpServletRequest();
        users.userLogin(login("Before2026!"), oldRequest);
        String oldHash = users.getById(id).getUserPassword();
        when(challenges.consumeResetToken("a".repeat(43))).thenReturn(
                new AuthChallengeStore.ResetTicket(id, org.cookie.usercenter.utils.PasswordUtils.sha256(oldHash)));
        assertTrue(resets.resetPassword(reset("After2026!")));
        assertThrows(BusinessException.class, () -> sessions.requireUser(oldRequest));
        assertThrows(BusinessException.class, () -> users.userLogin(login("Before2026!"), new MockHttpServletRequest()));
        assertEquals(id, users.userLogin(login("After2026!"), new MockHttpServletRequest()).getId());
        assertEquals("resetuser", users.getById(id).getUserAccount());
        assertEquals(0, users.getById(id).getStatus());
        assertEquals(0, users.getById(id).getIdentity());
    }

    @Test
    void invalidOrMismatchedPasswordCannotConsumeTheResetTicket() {
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset("different")));
        var weak = reset("weak"); weak.setNewPassword("weak");
        assertThrows(BusinessException.class, () -> resets.resetPassword(weak));
        verify(challenges, never()).consumeResetToken(anyString());
    }

    @Test
    void expiredOrCredentialChangedTicketCannotChangePassword() {
        long id = create();
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset("After2026!")));
        when(challenges.consumeResetToken(anyString())).thenReturn(new AuthChallengeStore.ResetTicket(id, "old-fingerprint"));
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset("After2026!")));
        assertEquals(id, users.userLogin(login("Before2026!"), new MockHttpServletRequest()).getId());
    }

    @Test
    void unchangedPasswordIsRejectedSoResetAlwaysRotatesTheCredentialStamp() {
        long id = create();
        String hash = users.getById(id).getUserPassword();
        when(challenges.consumeResetToken(anyString())).thenReturn(new AuthChallengeStore.ResetTicket(
                id, org.cookie.usercenter.utils.PasswordUtils.sha256(hash)));
        var input = reset("Before2026!"); input.setNewPassword("Before2026!");
        assertThrows(BusinessException.class, () -> resets.resetPassword(input));
        assertEquals(hash, users.getById(id).getUserPassword());
    }

    @Test
    void oldTicketIsRejectedAfterVersionChangesEvenWhenPasswordFingerprintMatches() {
        long id = create();
        String hash = users.getById(id).getUserPassword();
        when(challenges.consumeResetToken(anyString())).thenReturn(new AuthChallengeStore.ResetTicket(
                id, org.cookie.usercenter.utils.PasswordUtils.sha256(hash), 0L));
        when(challenges.credentialEpoch(id)).thenReturn(2L);
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset("After2026!")));
        assertEquals(hash, users.getById(id).getUserPassword());
    }

    @Test
    void codeVerificationIssuesTicketBoundToDatabaseUserAndSendingDoesNotExposeExistence() throws Exception {
        long id = create();
        var verify = new PasswordResetVerifyRequest();
        verify.setEmail("reset@example.com"); verify.setVerificationCode("Ab1234");
        when(challenges.issueResetToken(eq(id), anyString())).thenReturn("b".repeat(43));
        var ticket = resets.verifyEmail(verify);
        assertEquals("b".repeat(43), ticket.resetToken());
        assertEquals(600, ticket.expiresIn());
        verify(emails).verifyCode("reset@example.com", "Ab1234", CodePurpose.RESET_PASSWORD);
        SendEmailRequest known = new SendEmailRequest(); known.setEmail("reset@example.com");
        SendEmailRequest unknown = new SendEmailRequest(); unknown.setEmail("unknown@example.com");
        assertTrue(resets.sendCode(known)); assertTrue(resets.sendCode(unknown));
        verify(emails).sendCode("reset@example.com", CodePurpose.RESET_PASSWORD, true);
        verify(emails).sendCode("unknown@example.com", CodePurpose.RESET_PASSWORD, false);
    }
}
