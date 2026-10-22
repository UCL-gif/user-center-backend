package org.cookie.usercenter.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.cookie.usercenter.common.BaseResponse;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.common.ResultUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR, "头像不能超过2MB，整个请求不能超过3MB");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public BaseResponse<?> missingUploadPart(MissingServletRequestPartException e) {
        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR, "缺少用户资料，请同时提交user字段");
    }

    //通过这个注解，表示要针对什么异常，做什么处理
    @ExceptionHandler(BusinessException.class)
    public BaseResponse businessExceptionHandler(BusinessException e) {
        log.warn("业务请求未通过：{}", e.getDescription());
        return ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "服务暂时不可用，请稍后重试", "");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public BaseResponse duplicateKeyHandler(DuplicateKeyException e) {
        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR, "账号或邮箱已存在，请更换后重试");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> validationExceptionHandler(MethodArgumentNotValidException e) {

        String description = "请求参数不合法";

        // 先取其中一个字段错误，作为本次提示。
        FieldError fieldError = e.getBindingResult().getFieldError();

        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            description = fieldError.getDefaultMessage();
        }

        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR, description);
    }

    /**
     * 请求体缺失或 JSON 无法读取时，尚未进入 DTO 字段校验。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse requestBodyExceptionHandler(HttpMessageNotReadableException e) {

        return ResultUtils.error(ErrorCode.PARAMETERS_ERROR, "请求体不能为空或JSON格式不正确");
    }
}
