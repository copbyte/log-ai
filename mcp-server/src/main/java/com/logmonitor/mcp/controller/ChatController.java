package com.logmonitor.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.logmonitor.mcp.client.LogServiceClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
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
    /** 单条消息最大长度（字符），前端与后端双重校验 */
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ChatClient chatClient;
    private final LogServiceClient logServiceClient;

    public ChatController(ChatClient chatClient, LogServiceClient logServiceClient) {
        this.chatClient = chatClient;
        this.logServiceClient = logServiceClient;
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
    public Map<String, String> chat(@RequestBody ChatRequest request,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        validateMessage(request.message());
        List<Message> messages = buildMessages(request);
        long start = System.currentTimeMillis();
        try {
            // defaultSystem + defaultToolCallbacks 由 ChatClient 自动注入
            String response = chatClient.prompt()
                    .messages(messages)
                    .toolContext(toolContext(authorization))
                    .call()
                    .content();
            logServiceClient.sendAudit("AI_CHAT", null,
                    String.format("message=%s, responseLength=%d, durationMs=%d",
                            truncate(request.message(), 200),
                            response == null ? 0 : response.length(),
                            System.currentTimeMillis() - start),
                    "SUCCESS", null, authorization);
            // 对话持久化：用户问题 + AI 回答一起入库，按透传 JWT 归属用户
            saveHistory(request.message(), response, authorization);
            return Map.of("response", response);
        } catch (Exception e) {
            logServiceClient.sendAudit("AI_CHAT", null,
                    truncate(request.message(), 200),
                    "FAIL", truncate(e.getMessage(), 300), authorization);
            throw e;
        }
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
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request,
                                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        validateMessage(request.message());
        List<Message> messages = buildMessages(request);
        StringBuilder answer = new StringBuilder();
        long start = System.currentTimeMillis();

        // chatClient.stream() 返回 Flux<ChatResponse>，.content() 提取为 Flux<String>
        return chatClient.prompt()
                .messages(messages)
                .toolContext(toolContext(authorization))
                .stream()
                .content()
                .doOnNext(answer::append)
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
                        .build()))
                // 流结束后统一上报审计（正常/异常均触发）
                .doFinally(signal -> {
                    logServiceClient.sendAudit("AI_CHAT_STREAM", null,
                            String.format("message=%s, responseLength=%d, durationMs=%d",
                                    truncate(request.message(), 200),
                                    answer.length(),
                                    System.currentTimeMillis() - start),
                            "SUCCESS", null, authorization);
                    // 对话持久化：保存用户问题与已生成回答（服务端会跳过空内容）
                    saveHistory(request.message(), answer.toString(), authorization);
                });
    }

    /** 保存一段对话到 log-service（按用户 JWT 归属，失败不影响对话） */
    private void saveHistory(String message, String response, String authorization) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", message));
        messages.add(Map.of("role", "assistant", "content", response == null ? "" : response));
        logServiceClient.saveChatHistory(messages, authorization);
    }

    /** 构造工具调用上下文：把用户 JWT 带给工具回调，供审计按用户归属 */
    private Map<String, Object> toolContext(String authorization) {
        Map<String, Object> context = new HashMap<>();
        if (authorization != null) {
            context.put("authorization", authorization);
        }
        return context;
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
            // 历史消息同样截断，防止多轮累计超出模型上下文
            String content = truncate(h.content(), MAX_MESSAGE_LENGTH);
            if ("user".equalsIgnoreCase(h.role())) {
                messages.add(new UserMessage(content));
            } else if ("assistant".equalsIgnoreCase(h.role())) {
                messages.add(new AssistantMessage(content));
            }
        }
        messages.add(new UserMessage(truncate(currentMessage, MAX_MESSAGE_LENGTH)));
        return messages;
    }

    /** 消息长度校验：空消息或超过 1000 字直接拒绝 */
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "输入过长，最多 " + MAX_MESSAGE_LENGTH + " 字");
        }
    }

    /** 截断字符串 */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
