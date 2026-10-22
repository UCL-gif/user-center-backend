package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.cookie.usercenter.utils.PasswordUtils;

/** 没有用户 ID/邮箱字段：修改谁的密码，由服务端签发的凭证决定。 */
@Data
public class PasswordResetRequest {
    @NotBlank(message = "请先完成邮箱验证")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "重置凭证无效，请重新验证邮箱")
    private String resetToken;
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = PasswordUtils.STRONG_PASSWORD_PATTERN,
            message = "密码须为8～20位，并包含大写字母、小写字母、数字和特殊字符")
    private String newPassword;
    @NotBlank(message = "确认密码不能为空")
    @Size(max = 20, message = "确认密码不能超过20个字符")
    private String checkPassword;
}
