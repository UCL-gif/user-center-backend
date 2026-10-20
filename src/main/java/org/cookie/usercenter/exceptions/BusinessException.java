package org.cookie.usercenter.exceptions;


import org.cookie.usercenter.common.ErrorCode;

/**
 * 该类是自定义的异常类，用于捕获全局异常
 */
public class BusinessException extends RuntimeException{
    
    private final int code;
    private final String description;
    
    public BusinessException(String message, int code, String description) {
        super(message);
        this.code = code;
        this.description = description;
    }
    //从ErrorCode枚举类中获得异常信息
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = errorCode.getDescription();
    }
    //可以自己传描述信息
    public BusinessException(ErrorCode errorCode, String description) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}
