package com.logmonitor.mcp.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 前端对话接口
 * 用户输入自然语言，ChatClient 调用 DeepSeek + Tool Calling 完成日志分析
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对话接口（同步）
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = chatClient.prompt()
                .user(message)
                .call()
                .content();
        return Map.of("response", response);
    }
}
