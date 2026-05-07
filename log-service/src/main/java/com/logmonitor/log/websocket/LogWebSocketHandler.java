package com.logmonitor.log.websocket;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: id={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: id={}", session.getId());
    }

    public void broadcastLogEntry(LogEntry logEntry) {
        String json = JSON.toJSONString(Map.of("type", "LOG_ENTRY", "data", logEntry));
        TextMessage message = new TextMessage(json);
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (Exception e) {
                    log.error("Failed to send WebSocket message: id={}", session.getId(), e);
                }
            }
        });
    }
}
