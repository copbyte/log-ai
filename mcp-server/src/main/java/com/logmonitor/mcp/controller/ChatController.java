package com.logmonitor.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 前端对话接口
 * 用户输入自然语言，ChatClient 调用 DeepSeek + Tool Calling 完成日志分析
 * 支持多轮对话：前端传入历史消息，后端拼接完整上下文给 LLM
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 最大保留的历史轮数（user+assistant 算 1 轮），避免 token 超限 */
    private static final int MAX_HISTORY_TURNS = 10;

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 单条历史消息（前端传入）
     */
    public record ChatMessage(String role, String content) {
    }

    /**
     * 对话请求体
     *
     * @param message 当前用户输入
     * @param history 历史消息（不包含当前 message），按时间升序
     */
    public record ChatRequest(String message, List<ChatMessage> history) {
    }

    /**
     * 对话接口（同步，支持多轮上下文）
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String currentMessage = request.message();
        List<ChatMessage> history = request.history() != null ? request.history() : List.of();

        // 截断历史，保留最近 MAX_HISTORY_TURNS*2 条（每轮 user+assistant）
        int maxHistoryItems = MAX_HISTORY_TURNS * 2;
        if (history.size() > maxHistoryItems) {
            history = history.subList(history.size() - maxHistoryItems, history.size());
        }

        // 拼接历史消息为 Spring AI Message 列表
        List<Message> messages = new ArrayList<>(history.size() + 1);
        for (ChatMessage h : history) {
            if (h == null || h.content() == null || h.content().isBlank()) {
                continue;
            }
            if ("user".equalsIgnoreCase(h.role())) {
                messages.add(new UserMessage(h.content()));
            } else if ("assistant".equalsIgnoreCase(h.role())) {
                messages.add(new AssistantMessage(h.content()));
            }
        }
        messages.add(new UserMessage(currentMessage));

        // defaultSystem + defaultToolCallbacks 由 ChatClient 自动注入
        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();
        return Map.of("response", response);
    }
}
