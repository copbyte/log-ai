package com.logmonitor.mcp.config;

import com.logmonitor.mcp.context.UserAuthContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;

/**
 * 工具回调包装器：把 ChatClient 传入的 toolContext 里的用户 JWT
 * 临时放到线程上下文，供 LogServiceClient 工具调用使用（调用结束后清理）。
 * <p>
 * 这样 AI 发起的日志查询会以真实用户身份调用 log-service，
 * 审计 LOG_QUERY 归到当前用户；外部 MCP 客户端无上下文时保持服务令牌。
 */
public class AuthAwareToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;

    public AuthAwareToolCallbackProvider(ToolCallbackProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return Arrays.stream(delegate.getToolCallbacks())
                .map(this::wrap)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback wrap(ToolCallback callback) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return callback.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                String authorization = null;
                if (toolContext != null && toolContext.getContext() != null) {
                    Object value = toolContext.getContext().get("authorization");
                    if (value instanceof String s && s.startsWith("Bearer ")) {
                        authorization = s;
                    }
                }
                UserAuthContext.set(authorization);
                try {
                    return callback.call(toolInput, toolContext);
                } finally {
                    UserAuthContext.clear();
                }
            }
        };
    }
}
