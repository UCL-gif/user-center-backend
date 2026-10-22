package org.cookie.usercenter.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SendEmailRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -5064877184296074191L;
    @NotBlank(message = "邮箱不能为空！")
    @Email(message = "邮箱格式不正确")
    @Size(max = 32, message = "邮箱长度不能超过32个字符")
    private String email;
}
