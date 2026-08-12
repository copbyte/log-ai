package com.logmonitor.log.auth;

/**
 * 当前请求认证上下文（线程级），由 JwtAuthFilter 填充，供审计/限流使用。
 */
public final class AuthContext {

    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void clear() {
        USERNAME.remove();
    }
}
