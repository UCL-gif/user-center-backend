package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.*;

import lombok.Data;

/** 管理员编辑资料请求：ID 定位已有记录，不接收密码。 */
@Data

public class UserUpdateRequest {
    @NotNull(message = "请选择要修改的用户")
    @Positive(message = "用户ID必须大于0")
    private Long id;

    @NotBlank(message = "账号不能为空！")
    @Pattern(
            regexp = "[\\p{L}\\p{N}_-]{4,32}",
            message = "账号须为4～32位字母、数字、下划线或短横线")
    private String userAccount;

    @Size(max = 32, message = "昵称不能超过32个字符")
    private String userName;

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

    @Min(value = 0)
    @Max(value = 1)
    @NotNull(message = "编辑用户时请填写状态")
    private Integer status;

    @Min(value = 0)
    @Max(value = 1)
    @NotNull(message = "编辑用户时请填写角色")
    private Integer identity;
}
