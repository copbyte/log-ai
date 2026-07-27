package com.logmonitor.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
        List<Message> messages = buildMessages(request);
        // defaultSystem + defaultToolCallbacks 由 ChatClient 自动注入
        String response = chatClient.prompt()
                .messages(messages)
                .call()
                .content();
        return Map.of("response", response);
    }

    /**
     * 流式对话接口（SSE，支持多轮上下文）
     * <p>
     * 响应 Content-Type: text/event-stream
     * 每个事件格式：
     *   event: delta\n
     *   data: 文本片段\n\n
     * 流结束发送一个：
     *   event: done\n
     *   data: [DONE]\n\n
     * <p>
     * Spring MVC 容器也支持返回 Flux&lt;ServerSentEvent&gt;，
     * 会通过 ResponseBodyEmitter 适配为 Servlet 异步流式响应。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request) {
        List<Message> messages = buildMessages(request);

        // chatClient.stream() 返回 Flux<ChatResponse>，.content() 提取为 Flux<String>
        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .data(chunk)
                        .build())
                // 流末尾追加 [DONE] 哨兵事件，便于前端判断结束
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(e.getMessage() != null ? e.getMessage() : "stream error")
                        .build()));
    }

    /**
     * 从 ChatRequest 构造 Spring AI Message 列表（历史截断 + 当前用户输入）
     * 同步 / 流式接口共用
     */
    private List<Message> buildMessages(ChatRequest request) {
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
        return messages;
    }
}
