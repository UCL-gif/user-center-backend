package org.cookie.usercenter.model.request;

import lombok.Data;

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
    
    private String userAccount;
    
    private String userPassword;
    
    private String checkPassword;
    
    private String email;
    
    private String verificationCode;
}
