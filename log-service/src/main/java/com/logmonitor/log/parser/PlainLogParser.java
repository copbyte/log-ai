package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;

/**
 * 兜底解析器：所有格式都不匹配时使用
 * <p>
 * 不解析时间/线程/类名，只从内容里关键词扫描日志级别，
 * 防止因格式不匹配导致日志完全丢失。
 */
public class PlainLogParser implements LogParser {

    @Override
    public LogEntry parse(String fileName, String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        return LogEntry.builder()
                .fileName(fileName)
                .logTime(LocalDateTime.now())
                .logLevel(detectLevel(line))
                .content(line)
                .build();
    }

    @Override
    public boolean matches(String sampleLine) {
        // 兜底 Parser 永远不主动 matches，只在其他全部失败时使用
        return false;
    }

    @Override
    public String name() {
        return "Plain";
    }

    private String detectLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("FATAL")) return "FATAL";
        if (upper.contains("ERROR")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("DEBUG")) return "DEBUG";
        if (upper.contains("TRACE")) return "TRACE";
        return "INFO";
    }
}
