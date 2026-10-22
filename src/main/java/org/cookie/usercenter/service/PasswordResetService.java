package org.cookie.usercenter.service;

import org.cookie.usercenter.model.request.PasswordResetRequest;
import org.cookie.usercenter.model.request.PasswordResetVerifyRequest;
import org.cookie.usercenter.model.request.SendEmailRequest;

/** 邮箱验证、一次性授权和密码重置的业务契约。 */
public interface PasswordResetService {
    boolean sendCode(SendEmailRequest request);

    ResetAuthorization verifyEmail(PasswordResetVerifyRequest request);

    boolean resetPassword(PasswordResetRequest request);

    record ResetAuthorization(String resetToken, int expiresIn) { }
}
