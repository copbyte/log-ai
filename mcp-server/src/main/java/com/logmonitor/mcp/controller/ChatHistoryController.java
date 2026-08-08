package com.logmonitor.mcp.controller;

import com.logmonitor.mcp.client.LogServiceClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对话历史查询接口（前端 -> mcp-server -> log-service）
 * <p>
 * 前端携带用户 JWT，mcp-server 原样透传给 log-service，
 * 由 log-service 验签后只返回当前用户自己的历史记录。
 */
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController {

    private final LogServiceClient logServiceClient;

    public ChatHistoryController(LogServiceClient logServiceClient) {
        this.logServiceClient = logServiceClient;
    }

    /** 分页查询当前用户对话历史 */
    @GetMapping
    public Map<String, Object> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return logServiceClient.getChatHistory(page, size, authorization);
    }
}
