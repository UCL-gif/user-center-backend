package org.cookie.usercenter.service;

/** 验证码、一次性密码重置凭证和认证版本的存储契约。 */
public interface AuthChallengeStore {
    int CODE_SECONDS = 60;
    int MAX_ATTEMPTS = 3;
    int RESET_TOKEN_SECONDS = 600;

    boolean issueCode(CodePurpose purpose, String email, String code);

    CodeResult consumeCode(CodePurpose purpose, String email, String input);

    void discardCode(CodePurpose purpose, String email, String code);

    String issueResetToken(long userId, String credentialStamp);

    ResetTicket consumeResetToken(String token);

    long credentialEpoch(long userId);

    void rotateCredentialEpoch(long userId);

    enum CodeResult { VALID, INVALID, LOCKED, EXPIRED }

    record ResetTicket(long userId, String credentialStamp, long credentialEpoch) {
        public ResetTicket(long userId, String credentialStamp) { this(userId, credentialStamp, 0L); }
    }
}
