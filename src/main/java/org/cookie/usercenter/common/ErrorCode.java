package org.cookie.usercenter.common;

import lombok.Getter;

/**
 * 全局错误码
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "ok", ""),
    PARAMETERS_ERROR(40000,"请求参数错误",""),
    PARAMETERS_NULL_ERROR(40001,"请求数据为空",""),
    NO_LOGIN(40100,"未登录",""),
    NO_PRIVILEGE(40101, "无权限", ""),
    SYSTEM_ERROR(50000, "系统内部异常", "");
    
    private final int code;
    private final String message;
    private final String description;
    
    ErrorCode(int code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }
    
}
