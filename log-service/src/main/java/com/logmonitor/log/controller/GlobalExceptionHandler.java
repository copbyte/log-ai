package com.logmonitor.log.controller;

import com.logmonitor.common.result.Result;
import com.logmonitor.log.auth.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：认证 401、参数/其他 500，统一返回 { code, message, data }。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public Result<Void> handleAuth(AuthException e) {
        return Result.fail(401, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("接口异常: {}", e.getMessage(), e);
        return Result.fail(500, "服务器内部错误");
    }
}
