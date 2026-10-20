package org.cookie.usercenter.model.request;

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
    
    private String userAccount;
    
    private String userPassword;
    
    
}
