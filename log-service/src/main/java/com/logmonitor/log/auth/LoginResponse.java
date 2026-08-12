package com.logmonitor.log.auth;

/**
 * 登录响应体
 */
public record LoginResponse(String token, long expiresInSeconds, String username) {
}
