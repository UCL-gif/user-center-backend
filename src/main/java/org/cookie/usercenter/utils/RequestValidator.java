package org.cookie.usercenter.utils;

import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.springframework.stereotype.Component;

/** 在 Service 入口执行 DTO 上已有的注解，保护测试或其他业务的直接调用。 */
@Component
public class RequestValidator {
    @Resource
    private Validator validator;

    public <T> void validate(T request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, "请求参数不能为空");
        }
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            String message = errors.stream()
                    .sorted(java.util.Comparator.comparing(error -> error.getPropertyPath().toString()))
                    .findFirst().orElseThrow().getMessage();
            throw new BusinessException(ErrorCode.PARAMETERS_ERROR, message);
        }
    }
}
