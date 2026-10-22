package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户登录请求体
 * @author  NingBao
 */
@Data
public class UserLoginRequest implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 770984247909691636L;
    
    @NotBlank(message = "账号不能为空！")
    //正则表达式校验
    @Pattern(
            regexp = "[\\p{L}\\p{N}_-]{4,32}",
            message = "账号须为4～32位字母、数字、下划线或短横线")
    private String userAccount;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 30, message = "密码长度须为8～30个字符")
    private String userPassword;

    /** 未勾选时使用普通会话；勾选后记住登录 7 天。 */
    private Boolean autoLogin = false;
    
    
}
