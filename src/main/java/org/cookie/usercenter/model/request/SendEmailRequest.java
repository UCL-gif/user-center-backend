package org.cookie.usercenter.model.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SendEmailRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -5064877184296074191L;
    private String email;
}
