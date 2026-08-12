package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.logmonitor.common.entity.ChatHistory;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.auth.AuthContext;
import com.logmonitor.log.auth.AuthException;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.service.ChatHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 对话历史接口：保存/查询当前登录用户的历史记录（数据按用户隔离）。
 * <p>
 * 保存由 mcp-server 透传用户 JWT 调用，username 取自登录态而非请求体。
 */
@RestController
@RequestMapping("/api/chat-history")
public class ChatHistoryController {

    /** 批量保存请求体：一段对话的多条消息 */
    public record MessagesRequest(List<MessageItem> messages) {
    }

    public record MessageItem(String role, String content) {
    }

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /** 保存当前用户的一段对话（问题 + 回答） */
    @PostMapping("/batch")
    @RateLimit(limit = 60, windowSeconds = 10, key = "chat-history:save")
    public Result<Void> saveBatch(@RequestBody MessagesRequest request) {
        String username = currentUsername();
        List<ChatHistory> messages = request.messages() == null ? List.of() : request.messages().stream()
                .map(m -> ChatHistory.builder()
                        .role(m.role())
                        .content(m.content())
                        .build())
                .toList();
        chatHistoryService.saveBatch(username, messages);
        return Result.success();
    }

    /** 查询当前用户的历史记录（分页，时间倒序） */
    @GetMapping
    @RateLimit(limit = 30, windowSeconds = 10, key = "chat-history:page")
    public Result<IPage<ChatHistory>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return Result.success(chatHistoryService.page(currentUsername(), page, size));
    }

    private String currentUsername() {
        String username = AuthContext.getUsername();
        if (username == null) {
            throw new AuthException("未认证");
        }
        return username;
    }
}
