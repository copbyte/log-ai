package com.logmonitor.mcp.context;

/**
 * 当前工具调用所属用户的 Authorization 头（线程级）。
 * <p>
 * 由 AuthAwareToolCallbackProvider 在 ToolCallback 执行前后设置/清理：
 * 工具方法运行时与 REST 调用同线程，ThreadLocal 可靠；
 * 外部 MCP 客户端调用时不经过该包装器，上下文为空，回退服务令牌。
 */
public final class UserAuthContext {

    private static final ThreadLocal<String> AUTHORIZATION = new ThreadLocal<>();

    private UserAuthContext() {
    }

    public static void set(String authorization) {
        AUTHORIZATION.set(authorization);
    }

    public static String get() {
        return AUTHORIZATION.get();
    }

    public static void clear() {
        AUTHORIZATION.remove();
    }
}
