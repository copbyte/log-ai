package com.logmonitor.mcp.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
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

    public LogServiceClient(@Value("${log-service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
                .retrieve()
                .body(Map.class);
    }
}
