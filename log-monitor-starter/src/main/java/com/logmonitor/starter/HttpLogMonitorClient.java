package com.logmonitor.starter;

import com.logmonitor.common.entity.LogEntry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 模式实现 — 通过 REST API 调用 log-service
 * <p>
 * 优点：服务无需引入 RabbitMQ，只需 HTTP 即可
 */
public class HttpLogMonitorClient implements LogMonitorClient {

    private final RestTemplate restTemplate;
    private final LogMonitorProperties properties;
    private final List<LogEntry> buffer = new ArrayList<>();

    public HttpLogMonitorClient(RestTemplate restTemplate, LogMonitorProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public synchronized void send(LogEntry logEntry) {
        buffer.add(logEntry);
        if (buffer.size() >= properties.getBatchSize()) {
            flush();
        }
    }

    @Override
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<LogEntry>> request = new HttpEntity<>(new ArrayList<>(buffer), headers);
            restTemplate.exchange(
                    properties.getUrl() + "/api/log/collect",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Void>() {});
        } catch (Exception e) {
            // 静默失败，不对业务调用方产生异常影响
        }
        buffer.clear();
    }
}
