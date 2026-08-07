package com.logmonitor.log.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 解析器注册中心测试：验证按样本自动绑定最优解析器。
 */
class LogParserRegistryTest {

    private final LogParserRegistry registry = new LogParserRegistry();

    @Test
    void bindsJsonParserForJsonLines() {
        List<String> sample = List.of(
                "{\"timestamp\":\"2026-08-01 10:00:00\",\"level\":\"INFO\",\"message\":\"start\"}",
                "{\"timestamp\":\"2026-08-01 10:00:01\",\"level\":\"ERROR\",\"message\":\"boom\"}");
        LogParser parser = registry.bind("/logs/app.json", sample);
        assertInstanceOf(JsonLogParser.class, parser);
        assertEquals("JsonLog", parser.name());
    }

    @Test
    void bindsLogbackParserForTypicalSpringLogLines() {
        List<String> sample = List.of(
                "2026-08-01 10:00:00.123 INFO  [http-nio-8081-exec-1] com.example.Service : hello",
                "2026-08-01 10:00:01.456 ERROR [main] com.example.Main : boom");
        LogParser parser = registry.bind("/logs/app.log", sample);
        assertInstanceOf(LogbackDefaultParser.class, parser);
    }

    @Test
    void fallbackToPlainParserWhenNothingMatches() {
        LogParser parser = registry.bind("/logs/raw.log", List.of("just a raw line without structure"));
        assertInstanceOf(PlainLogParser.class, parser);
    }
}
