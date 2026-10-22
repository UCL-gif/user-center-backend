package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 邮箱约束复用发送验证码请求；验证码只验证邮箱所有权。 */
@Data
public class PasswordResetVerifyRequest extends SendEmailRequest {
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "[a-zA-Z0-9]{6}", message = "验证码须为6位字母或数字")
    private String verificationCode;
}
