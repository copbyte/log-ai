package com.logmonitor.log.auth;

/**
 * 认证异常：用户名密码错误、未认证等，由全局异常处理器转为 401。
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
