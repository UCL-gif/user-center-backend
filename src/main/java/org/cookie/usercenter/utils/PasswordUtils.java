package org.cookie.usercenter.utils;

import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PasswordUtils {
    public static final String STRONG_PASSWORD_PATTERN = "^(?![A-Za-z0-9]+$)(?![a-z0-9\\W]+$)(?![A-Za-z\\W]+$)(?![A-Z0-9\\W]+$)[a-zA-Z0-9\\W]{8,20}$";
    private PasswordUtils() { }

    /** 与已有账号兼容；注册、新增、登录、找回密码必须使用相同规则。 */
    public static String hashPassword(String password) {
        return DigestUtils.md5DigestAsHex(("NingBao" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** Redis 中只保存验证码/重置凭证的摘要，不保存它们的明文。 */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
