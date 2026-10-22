package org.cookie.usercenter.service.impl;

import org.cookie.usercenter.service.AuthChallengeStore;
import org.cookie.usercenter.service.CodePurpose;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 仅显式指定参数时连接隔离的测试 Redis（16389）；绝不连接正式 6379。 */
@EnabledIfSystemProperty(named = "auth.redis.integration", matches = "true")
class AuthChallengeStoreTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private AuthChallengeStoreImpl store;
    private String email;

    @BeforeAll
    static void connect() {
        connection = new LettuceConnectionFactory("127.0.0.1", 16389);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() { connection.destroy(); }

    @BeforeEach
    void setup() {
        store = new AuthChallengeStoreImpl(redis);
        email = UUID.randomUUID() + "@example.com";
    }

    @Test
    void thirdMistakeLocksEvenCorrectCodeAndResendingCannotResetAttempts() {
        assertTrue(store.issueCode(CodePurpose.REGISTER, email, "Ab1234"));
        assertEquals(AuthChallengeStore.CodeResult.INVALID, store.consumeCode(CodePurpose.REGISTER, email, "bad001"));
        assertEquals(AuthChallengeStore.CodeResult.INVALID, store.consumeCode(CodePurpose.REGISTER, email, "bad002"));
        assertEquals(AuthChallengeStore.CodeResult.LOCKED, store.consumeCode(CodePurpose.REGISTER, email, "bad003"));
        assertEquals(AuthChallengeStore.CodeResult.LOCKED, store.consumeCode(CodePurpose.REGISTER, email, "Ab1234"));
        assertFalse(store.issueCode(CodePurpose.REGISTER, email, "New123"));
    }

    @Test
    void codesExpireAfterOneMinuteAndSuccessfulVerificationIsOneUse() {
        assertTrue(store.issueCode(CodePurpose.REGISTER, email, "Ab1234"));
        String key = store.codeKey(CodePurpose.REGISTER, email);
        assertTrue(redis.getExpire(key) > 0 && redis.getExpire(key) <= 60);
        assertEquals(AuthChallengeStore.CodeResult.VALID, store.consumeCode(CodePurpose.REGISTER, email, "Ab1234"));
        assertEquals(AuthChallengeStore.CodeResult.EXPIRED, store.consumeCode(CodePurpose.REGISTER, email, "Ab1234"));
        // 消费验证码之后，发送间隔仍然存在。
        assertFalse(store.issueCode(CodePurpose.REGISTER, email, "New123"));
    }

    @Test
    void expiredAndDifferentPurposeCodesCannotBeReused() {
        store.issueCode(CodePurpose.REGISTER, email, "Ab1234");
        assertEquals(AuthChallengeStore.CodeResult.EXPIRED, store.consumeCode(CodePurpose.RESET_PASSWORD, email, "Ab1234"));
        assertTrue(store.issueCode(CodePurpose.RESET_PASSWORD, email, "Rs1234"));
        redis.expire(store.codeKey(CodePurpose.RESET_PASSWORD, email), Duration.ZERO);
        assertEquals(AuthChallengeStore.CodeResult.EXPIRED, store.consumeCode(CodePurpose.RESET_PASSWORD, email, "Rs1234"));
        assertEquals(AuthChallengeStore.CodeResult.VALID, store.consumeCode(CodePurpose.REGISTER, email, "Ab1234"));
    }

    @Test
    void resetTicketIsBoundToUserExpiresAndIsSingleUse() {
        String token = store.issueResetToken(42, "password-fingerprint");
        assertEquals(43, token.length());
        assertTrue(redis.getExpire(store.resetKey(token)) <= 600);
        var ticket = store.consumeResetToken(token);
        assertEquals(42, ticket.userId());
        assertEquals("password-fingerprint", ticket.credentialStamp());
        assertNull(store.consumeResetToken(token));
        String expired = store.issueResetToken(43, "other");
        redis.expire(store.resetKey(expired), Duration.ZERO);
        assertNull(store.consumeResetToken(expired));
    }

    @Test
    void resetTicketKeepsItsOriginalCredentialEpochEvenIfPasswordFingerprintIsReused() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String token = store.issueResetToken(userId, "fingerprint-A");
        store.rotateCredentialEpoch(userId);
        store.rotateCredentialEpoch(userId);
        var oldTicket = store.consumeResetToken(token);
        assertEquals(0L, oldTicket.credentialEpoch());
        assertEquals(2L, store.credentialEpoch(userId));
    }
}
