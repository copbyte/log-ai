package com.logmonitor.mcp.config;

import com.logmonitor.mcp.tool.LogAnalysisTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置
 * 使用 DeepSeek（兼容 OpenAI 协议），通过 spring.ai.openai 配置
 * ChatClient 自动注册 @Tool 注解的方法作为 Tool Calling
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ToolCallbackProvider logAnalysisToolProvider(LogAnalysisTools tools) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
        // 包装一层：把请求级用户 JWT 透传给工具调用（AI 发起的查询按用户审计）
        return new AuthAwareToolCallbackProvider(provider);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider toolProvider) {
        return builder
                .defaultSystem("你是一个专业的日志分析助手，可以帮助用户检索日志、分析异常、定位根因。"
                        + "当用户询问日志相关问题时，请调用相应的工具获取数据，然后给出分析结论。"
                        + "回答使用中文，结构清晰。")
                .defaultToolCallbacks(toolProvider)
                .build();
    }
}
