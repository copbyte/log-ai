package com.logmonitor.log.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 格式日志解析器
 * <p>
 * 适配 logstash-encoder / logback-json-encoder 等输出格式
 * 示例：{"@timestamp":"2026-07-19T21:33:35.138","level":"INFO","thread":"main","logger_name":"c.l.l.App","message":"Starting..."}
 */
@Slf4j
public class JsonLogParser implements LogParser {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public LogEntry parse(String fileName, String line) {
        try {
            JSONObject json = JSON.parseObject(line);
            if (json == null) {
                return null;
            }
            return LogEntry.builder()
                    .fileName(fileName)
                    .logTime(parseTime(json.getString("@timestamp"),
                            json.getString("timestamp"),
                            json.getString("time")))
                    .logLevel(json.getString("level"))
                    .threadName(json.getString("thread_name") != null
                            ? json.getString("thread_name")
                            : json.getString("thread"))
                    .className(json.getString("logger_name") != null
                            ? json.getString("logger_name")
                            : json.getString("logger"))
                    .content(json.getString("message") != null
                            ? json.getString("message")
                            : json.getString("msg"))
                    .build();
        } catch (Exception e) {
            log.debug("JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean matches(String sampleLine) {
        String trimmed = sampleLine.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        try {
            JSON.parseObject(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String name() {
        return "JsonLog";
    }

    private LocalDateTime parseTime(String... candidates) {
        for (String ts : candidates) {
            if (ts == null || ts.isBlank()) {
                continue;
            }
            try {
                return LocalDateTime.parse(ts.replace(' ', 'T'), ISO_FORMAT);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }
}
