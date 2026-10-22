package org.cookie.usercenter.service;

import org.cookie.usercenter.service.impl.AuthSessionServiceImpl;

import jakarta.servlet.http.HttpSession;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.mapper.UserMapper;
import org.cookie.usercenter.model.domain.User;
import org.cookie.usercenter.testsupport.TestSessionConfiguration.MemoryIndexedSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceTest {
    private UserMapper mapper;
    private AuthSessionService service;
    private User user;
    private AuthChallengeStore challenges;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        challenges = mock(AuthChallengeStore.class);
        service = new AuthSessionServiceImpl(mapper, new MemoryIndexedSessionRepository(), challenges);
        user = user(42L);
        when(mapper.selectById(42L)).thenAnswer(call -> user);
    }

    @Test
    void loginReplacesOldSessionAndStoresOnlySerializableAuthenticationValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession old = request.getSession();
        String oldId = old.getId();
        old.setAttribute("untrusted", "old data");
        service.login(user, request, false);
        HttpSession session = request.getSession(false);
        assertNotEquals(oldId, session.getId());
        assertThrows(IllegalStateException.class, () -> old.getAttribute("untrusted"));
        assertNull(session.getAttribute("untrusted"));
        assertEquals(42L, session.getAttribute(AuthSessionService.USER_ID_ATTRIBUTE));
        assertEquals(1800, session.getMaxInactiveInterval());
        assertFalse((Boolean) session.getAttribute(AuthSessionService.REMEMBER_ME_ATTRIBUTE));
        assertNull(session.getAttribute(AuthSessionService.ABSOLUTE_EXPIRES_AT_ATTRIBUTE));
        assertTrue(Collections.list(session.getAttributeNames()).stream().map(session::getAttribute)
                .allMatch(value -> value instanceof String || value instanceof Long || value instanceof Boolean));
        assertNotEquals(user.getUserPassword(), session.getAttribute(AuthSessionService.CREDENTIAL_STAMP_ATTRIBUTE));
    }

    @Test
    void rememberedLoginHasSevenDayIdleAndAbsoluteExpiry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        long before = System.currentTimeMillis();
        service.login(user, request, true);
        HttpSession session = request.getSession(false);
        assertEquals(604800, session.getMaxInactiveInterval());
        assertTrue((Boolean) session.getAttribute(AuthSessionService.REMEMBER_ME_ATTRIBUTE));
        long expiresAt = (Long) session.getAttribute(AuthSessionService.ABSOLUTE_EXPIRES_AT_ATTRIBUTE);
        assertTrue(expiresAt >= before + 604800000L);
        assertTrue(expiresAt <= System.currentTimeMillis() + 604800000L);
    }

    @Test
    void rememberedSessionCannotExtendItsAbsoluteExpiryThroughActivity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.login(user, request, true);
        request.getSession(false).setAttribute(AuthSessionService.ABSOLUTE_EXPIRES_AT_ATTRIBUTE, System.currentTimeMillis() - 1);
        assertNoLogin(request);
        assertNull(request.getSession(false));
    }

    @Test
    void authorizationReadsTheCurrentRoleAndProfileFromDatabase() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.login(user, request, false);
        User fresh = user(42L);
        fresh.setIdentity(1);
        fresh.setUserName("new profile");
        user = fresh;
        assertSame(fresh, service.requireUser(request));
        user.setIdentity(0);
        assertEquals(0, service.requireUser(request).getIdentity());
    }

    @Test
    void disabledDeletedMissingOrUnsupportedRoleInvalidatesSession() {
        for (int scenario = 0; scenario < 4; scenario++) {
            user = user(42L);
            MockHttpServletRequest request = new MockHttpServletRequest();
            service.login(user, request, false);
            switch (scenario) {
                case 0 -> user.setStatus(1);
                case 1 -> user.setDeleted(1);
                case 2 -> user.setIdentity(2);
                case 3 -> user = null;
            }
            assertNoLogin(request);
            assertNull(request.getSession(false));
        }
    }

    @Test
    void passwordChangeRejectsExistingAndInFlightOldCredentialSessions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User authenticatedBeforeReset = user(42L);
        service.login(authenticatedBeforeReset, request, false);
        user.setUserPassword("reset-password-hash");
        assertNoLogin(request);
        MockHttpServletRequest inFlightLogin = new MockHttpServletRequest();
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(authenticatedBeforeReset, inFlightLogin, false));
        assertEquals(40100, error.getCode());
        assertNull(inFlightLogin.getSession(false));
    }

    @Test
    void logoutIsIdempotentAndUnauthenticatedCheckDoesNotCreateSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNoLogin(request);
        assertNull(request.getSession(false));
        service.logout(request);
        service.login(user, request, false);
        service.logout(request);
        service.logout(request);
        assertNull(request.getSession(false));
    }

    @Test
    void malformedSessionIsRejectedInsteadOfTrustingLegacyUserObject() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("userLoginState", user);
        request.getSession().setAttribute(AuthSessionService.USER_ID_ATTRIBUTE, "42");
        assertNoLogin(request);
        assertNull(request.getSession(false));
    }

    @Test
    void oldSessionDoesNotReviveWhenPasswordReturnsToAnEarlierValue() {
        var request = new MockHttpServletRequest();
        service.login(user, request, true);
        // 密码从 A 改为 B 再改为 A，摘要相同，但认证版本已经变化。
        when(challenges.credentialEpoch(42L)).thenReturn(2L);
        assertNoLogin(request);
    }

    private void assertNoLogin(MockHttpServletRequest request) {
        assertEquals(40100, assertThrows(BusinessException.class, () -> service.requireUser(request)).getCode());
    }

    static User user(long id) {
        User value = new User();
        value.setId(id);
        value.setUserName("original");
        value.setUserPassword("stored-password-hash");
        value.setStatus(0);
        value.setDeleted(0);
        value.setIdentity(0);
        return value;
    }
}
