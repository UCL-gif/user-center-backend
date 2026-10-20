package org.cookie.usercenter.common;

/**
 * 返回工具类
 * @author Ningbao
 */
public class ResultUtils {
    /**
     * 成功
     */
    public static <T> BaseResponse<T> success(T data){
        return new BaseResponse<>(0,data,"ok");
    }
    
    /**
     * 失败
     */
    public static BaseResponse error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }
    
    /**
     * 支持用户自己输入消息和描述
     */
    public static BaseResponse error(ErrorCode errorCode,String message,String description) {
        return new BaseResponse<>(errorCode.getCode(),null, message, description);
    }
    /**
     * 支持用户自己描述
     */
    public static BaseResponse error(ErrorCode errorCode,String description) {
        return new BaseResponse<>(errorCode.getCode(),null, errorCode.getMessage(), description);
    }
    public static BaseResponse error(int code,String message ,String description) {
        return new BaseResponse<>(code,null, message, description);
    }
    
}
