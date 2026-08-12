package com.logmonitor.mcp.client;

import com.logmonitor.mcp.context.UserAuthContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 调用 log-service REST API 的客户端
 */
@Slf4j
@Component
public class LogServiceClient {

    private final RestClient restClient;
    private final String serviceToken;

    public LogServiceClient(@Value("${log-service.url}") String baseUrl,
                            @Value("${log-service.token:service-token-demo}") String serviceToken) {
        // 服务间调用令牌：与 log-service 的 app.security.service-token 保持一致
        this.serviceToken = serviceToken;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .build();
    }

    /**
     * 分页搜索日志
     */
    public Map<String, Object> searchLogs(String logLevel, String keyword, String traceId,
                                           String serviceName, int page, int size) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/log/entries")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (logLevel != null) uriBuilder.queryParam("logLevel", logLevel);
                    if (keyword != null) uriBuilder.queryParam("keyword", keyword);
                    if (traceId != null) uriBuilder.queryParam("traceId", traceId);
                    if (serviceName != null) uriBuilder.queryParam("serviceName", serviceName);
                    return uriBuilder.build();
                })
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 按 TraceID 查询关联日志
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getLogsByTraceId(String traceId) {
        Map<String, Object> response = restClient.get()
                .uri("/api/log/trace/{traceId}", traceId)
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return List.of();
    }

    /**
     * 查询单条日志详情
     */
    public Map<String, Object> getLogById(Long id) {
        return restClient.get()
                .uri("/api/log/entries/{id}", id)
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 查询告警统计
     */
    public Map<String, Object> getAlertStats() {
        return restClient.get()
                .uri("/api/alert/stats")
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 分页查询告警列表（可按状态、严重级别筛选）
     */
    public Map<String, Object> getAlerts(String status, Integer severity, int page, int size) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/alert")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (status != null) uriBuilder.queryParam("status", status);
                    if (severity != null) uriBuilder.queryParam("severity", severity);
                    return uriBuilder.build();
                })
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 查询安全日志（按源IP、动作筛选）
     * 注：log-service 的 /api/log/entries 需支持 srcIp、action 参数
     */
    public Map<String, Object> getSecurityLogs(String srcIp, String action, int page, int size) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/log/entries")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (srcIp != null) uriBuilder.queryParam("srcIp", srcIp);
                    if (action != null) uriBuilder.queryParam("action", action);
                    return uriBuilder.build();
                })
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 上报 AI 调用审计到 log-service（失败不影响对话，仅记 WARN）
     *
     * @param userAuthorization 前端透传的 Bearer JWT（可为 null）；非空时用它调用 log-service，
     *                          由 log-service 验签后记录真实操作用户，避免所有 AI 审计都记成 service
     */
    public void sendAudit(String operation, String username, String params, String result, String errorMessage,
                          String userAuthorization) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("operation", operation);
            body.put("username", username);
            body.put("params", params);
            body.put("result", result);
            body.put("errorMessage", errorMessage);
            restClient.post()
                    .uri("/api/audit")
                    // 覆盖默认的服务令牌：前端 JWT 有效时按真实用户审计，否则回退服务身份
                    .headers(h -> h.set(HttpHeaders.AUTHORIZATION, resolveAuthorization(userAuthorization)))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("上报审计到 log-service 失败: {}", e.getMessage());
        }
    }

    /** 优先使用前端 JWT，缺失时回退为服务间令牌 */
    private String resolveAuthorization(String userAuthorization) {
        if (userAuthorization != null && userAuthorization.startsWith("Bearer ")) {
            return userAuthorization;
        }
        return "Bearer " + serviceToken;
    }

    /** 工具调用场景：优先使用工具上下文中的用户 JWT，否则回退服务令牌 */
    private String resolveAuthorization() {
        String userAuth = UserAuthContext.get();
        if (userAuth != null && userAuth.startsWith("Bearer ")) {
            return userAuth;
        }
        return "Bearer " + serviceToken;
    }

    /**
     * 保存一段对话历史（用户问题 + AI 回答）到 log-service，按透传 JWT 归属用户。
     * 无用户 JWT（如 MCP 客户端直接调用）不保存，避免混入 service 共享桶；失败不影响对话。
     */
    public void saveChatHistory(List<Map<String, String>> messages, String userAuthorization) {
        if (userAuthorization == null || !userAuthorization.startsWith("Bearer ")) {
            log.debug("无用户 JWT，跳过对话历史保存");
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("messages", messages);
            restClient.post()
                    .uri("/api/chat-history/batch")
                    .headers(h -> h.set(HttpHeaders.AUTHORIZATION, userAuthorization))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("保存对话历史到 log-service 失败: {}", e.getMessage());
        }
    }

    /**
     * 分页查询当前用户对话历史（透传用户 JWT，由 log-service 按登录态隔离数据）。
     */
    public Map<String, Object> getChatHistory(int page, int size, String userAuthorization) {
        if (userAuthorization == null || !userAuthorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证或登录已过期");
        }
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/chat-history")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .headers(h -> h.set(HttpHeaders.AUTHORIZATION, userAuthorization))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证或登录已过期");
        }
    }
}
