package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.cookie.usercenter.utils.PasswordUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求体
 * @author NingBao
 */
@Data
public class UserRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -5821876967152300732L;

    @NotBlank(message = "账号不能为空！")
    @Pattern(
            regexp = "[\\p{L}\\p{N}_-]{4,32}",
            message = "账号须为4～32位字母、数字、下划线或短横线")
    private String userAccount;

    @NotBlank(message = "密码不能为空！")
    @Pattern(regexp = PasswordUtils.STRONG_PASSWORD_PATTERN,
            message = "密码须为8～20位，并包含大写字母、小写字母、数字和特殊字符")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    @NotBlank(message = "邮箱不能为空！")
    @Email(message = "邮箱格式不正确")
    @Size(max = 32, message = "邮箱长度不能超过32个字符")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(
            regexp = "[a-zA-Z0-9]{6}",
            message = "验证码须为6位字母或数字"
    )
    private String verificationCode;
}
