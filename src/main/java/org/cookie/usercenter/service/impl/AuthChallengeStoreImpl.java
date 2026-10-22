package org.cookie.usercenter.service.impl;

import org.cookie.usercenter.service.AuthChallengeStore;
import org.cookie.usercenter.service.CodePurpose;

import org.cookie.usercenter.utils.PasswordUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Lua 保证并发请求不会重复消费或绕过 3 次限制。 */
@Component
public class AuthChallengeStoreImpl implements AuthChallengeStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            redis.call('HSET', KEYS[1], 'digest', ARGV[1], 'attempts', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local digest = redis.call('HGET', KEYS[1], 'digest')
            if not digest then return -1 end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if attempts >= tonumber(ARGV[2]) then return -2 end
            if digest == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
            attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[2]) then return -2 end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> DISCARD = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'digest') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    public AuthChallengeStoreImpl(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public boolean issueCode(CodePurpose purpose, String email, String code) {
        return Long.valueOf(1).equals(redis.execute(ISSUE,
                List.of(codeKey(purpose, email), codeKey(purpose, email) + ":cooldown"),
                PasswordUtils.sha256(code), String.valueOf(CODE_SECONDS)));
    }

    @Override
    public CodeResult consumeCode(CodePurpose purpose, String email, String input) {
        Long result = redis.execute(VERIFY, List.of(codeKey(purpose, email)),
                PasswordUtils.sha256(input), String.valueOf(MAX_ATTEMPTS));
        if (Long.valueOf(1).equals(result)) return CodeResult.VALID;
        if (Long.valueOf(-2).equals(result)) return CodeResult.LOCKED;
        if (Long.valueOf(-1).equals(result)) return CodeResult.EXPIRED;
        return CodeResult.INVALID;
    }

    @Override
    public void discardCode(CodePurpose purpose, String email, String code) {
        redis.execute(DISCARD, List.of(codeKey(purpose, email)), PasswordUtils.sha256(code));
    }

    @Override
    public String issueResetToken(long userId, String credentialStamp) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(resetKey(token), userId + ":" + credentialStamp + ":" + credentialEpoch(userId),
                Duration.ofSeconds(RESET_TOKEN_SECONDS));
        return token;
    }

    @Override
    public ResetTicket consumeResetToken(String token) {
        String value = redis.opsForValue().getAndDelete(resetKey(token));
        if (value == null) return null;
        String[] parts = value.split(":", 3);
        return new ResetTicket(Long.parseLong(parts[0]), parts[1], Long.parseLong(parts[2]));
    }

    /** 独立的认证版本：密码改回历史值时，也不能让历史会话或重置凭证重新有效。 */
    @Override
    public long credentialEpoch(long userId) {
        String value = redis.opsForValue().get("user-center:auth:epoch:" + userId);
        return value == null ? 0L : Long.parseLong(value);
    }

    @Override
    public void rotateCredentialEpoch(long userId) {
        redis.opsForValue().increment("user-center:auth:epoch:" + userId);
    }

    String codeKey(CodePurpose purpose, String email) {
        // 与常见的 MySQL 邮箱不区分大小写规则一致，不能换个大小写就绕过发送间隔。
        return "user-center:auth:code:" + purpose.name() + ":" + PasswordUtils.sha256(email.toLowerCase(Locale.ROOT));
    }
    String resetKey(String token) { return "user-center:auth:reset:" + PasswordUtils.sha256(token); }
}
