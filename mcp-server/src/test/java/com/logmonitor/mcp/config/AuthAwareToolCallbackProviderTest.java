package com.logmonitor.mcp.config;

import com.logmonitor.mcp.context.UserAuthContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 工具回调包装器测试：用户 JWT 透传到工具调用线程，调用结束后清理。
 */
class AuthAwareToolCallbackProviderTest {

    @Test
    void propagatesUserAuthorizationToToolCall() {
        AtomicReference<String> seen = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return null;
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                seen.set(UserAuthContext.get());
                return "ok";
            }
        };

        ToolCallbackProvider provider = new AuthAwareToolCallbackProvider(() -> new ToolCallback[]{callback});
        ToolCallback wrapped = provider.getToolCallbacks()[0];
        wrapped.call("{}", new ToolContext(Map.of("authorization", "Bearer demo-jwt")));

        assertEquals("Bearer demo-jwt", seen.get());
        // 调用结束后必须清理，避免线程污染后续请求
        assertNull(UserAuthContext.get());
    }

    @Test
    void emptyContextFallsBackToNull() {
        AtomicReference<String> seen = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return null;
            }

            @Override
            public String call(String toolInput) {
                seen.set(UserAuthContext.get());
                return "ok";
            }
        };

        ToolCallbackProvider provider = new AuthAwareToolCallbackProvider(() -> new ToolCallback[]{callback});
        provider.getToolCallbacks()[0].call("{}");

        assertNull(seen.get());
        assertNull(UserAuthContext.get());
    }
}
