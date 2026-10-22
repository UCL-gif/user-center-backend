package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.*;

import lombok.Data;

/** 管理员新增请求：没有 ID，初始密码必填，状态只允许正常。 */
@Data

public class UserAddRequest {
    @NotBlank(message = "账号不能为空！")
    @Pattern(
            regexp = "[\\p{L}\\p{N}_-]{4,32}",
            message = "账号须为4～32位字母、数字、下划线或短横线")
    private String userAccount;

    @Size(max = 32, message = "昵称不能超过32个字符")
    private String userName;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 20, message = "初始密码须为8～20位")
    private String userPassword;

    @Size(max = 1024, message = "头像地址不能超过1024个字符")
    private String avatarUrl;

    @Min(value = 0)
    @Max(value = 1)
    private Integer gender;

    @Size(max = 20, message = "电话不能超过20个字符")
    @Pattern(regexp = "[0-9]*", message = "电话只能包含数字")
    private String phone;

    @NotBlank(message = "邮箱不能为空！")
    @Email(message = "邮箱格式不正确")
    @Size(max = 32, message = "邮箱长度不能超过32个字符")
    private String email;

    @Min(value = 0, message = "新增用户只能处于正常状态")
    @Max(value = 0, message = "新增用户只能处于正常状态")
    private Integer status;

    @Min(value = 0)
    @Max(value = 1)
    private Integer identity;
}
