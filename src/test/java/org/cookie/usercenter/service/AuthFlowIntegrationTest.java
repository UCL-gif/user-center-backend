package org.cookie.usercenter.service;

import jakarta.annotation.Resource;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.model.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** H2 + 隔离 Redis + 内存邮件：执行完整服务链，不发送真实邮件、不改真实数据库。 */
@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=16389",
        "spring.data.redis.password=", "spring.data.redis.database=0"})
@ActiveProfiles("test")
@Transactional
@EnabledIfSystemProperty(named = "auth.redis.integration", matches = "true")
class AuthFlowIntegrationTest {
    @Resource EmailService emails;
    @Resource UserService users;
    @Resource PasswordResetService resets;
    @Resource AuthSessionService sessions;
    @MockitoBean JavaMailSender sender;
    private final AtomicReference<MimeMessage> delivered = new AtomicReference<>();

    @BeforeEach
    void setupMail() {
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(call -> { delivered.set(call.getArgument(0)); return null; }).when(sender).send(any(MimeMessage.class));
    }

    private UserRegisterRequest registration() {
        String account = "test" + UUID.randomUUID().toString().substring(0, 8);
        UserRegisterRequest input = new UserRegisterRequest();
        input.setUserAccount(account); input.setEmail(account + "@example.com");
        input.setUserPassword("Before2026!"); input.setCheckPassword("Before2026!");
        return input;
    }

    private String deliveredCode() throws Exception {
        var match = Pattern.compile("验证码是：([a-zA-Z0-9]{6})").matcher((String) delivered.get().getContent());
        assertTrue(match.find());
        return match.group(1);
    }

    @Test
    void registrationLocksAfterThreeWrongAttemptsEvenWhenTheFourthCodeIsCorrect() throws Exception {
        var input = registration();
        assertTrue(emails.sendEmailCode(input.getEmail()));
        String code = deliveredCode();
        input.setVerificationCode("000000");
        for (int i = 0; i < 3; i++) assertThrows(BusinessException.class, () -> users.userRegister(input));
        input.setVerificationCode(code);
        assertTrue(assertThrows(BusinessException.class, () -> users.userRegister(input)).getDescription().contains("3次"));
        assertEquals(0, users.count());
        assertFalse(emails.sendEmailCode(input.getEmail()));
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void fullRegistrationAndResetChangesPasswordAndConsumesTicketOnce() throws Exception {
        var registration = registration();
        emails.sendEmailCode(registration.getEmail());
        registration.setVerificationCode(deliveredCode());
        long id = users.userRegister(registration);
        UserLoginRequest login = new UserLoginRequest();
        login.setUserAccount(registration.getUserAccount()); login.setUserPassword("Before2026!");
        var oldRequest = new MockHttpServletRequest();
        assertEquals(id, users.userLogin(login, oldRequest).getId());
        SendEmailRequest send = new SendEmailRequest(); send.setEmail(registration.getEmail());
        assertTrue(resets.sendCode(send));
        var verification = new PasswordResetVerifyRequest();
        verification.setEmail(send.getEmail()); verification.setVerificationCode(deliveredCode());
        var authorization = resets.verifyEmail(verification);
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setResetToken(authorization.resetToken()); reset.setNewPassword("After2026!"); reset.setCheckPassword("After2026!");
        assertTrue(resets.resetPassword(reset));
        assertThrows(BusinessException.class, () -> resets.resetPassword(reset));
        assertThrows(BusinessException.class, () -> sessions.requireUser(oldRequest));
        assertThrows(BusinessException.class, () -> users.userLogin(login, new MockHttpServletRequest()));
        login.setUserPassword("After2026!");
        assertEquals(id, users.userLogin(login, new MockHttpServletRequest()).getId());
    }

    @Test
    void unknownEmailGetsSameResponseAndDoesNotReceiveMail() {
        SendEmailRequest unknown = new SendEmailRequest();
        unknown.setEmail(registration().getEmail());
        assertTrue(resets.sendCode(unknown));
        assertTrue(resets.sendCode(unknown));
        verify(sender, never()).send(any(MimeMessage.class));
    }
}
