package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot / logback 默认控制台格式解析器
 * <p>
 * 格式：yyyy-MM-dd HH:mm:ss.SSS  LEVEL [thread] class : message
 * 注意：默认 CONSOLE_LOG_PATTERN 中级别和线程之间是双空格，分隔符是 " : " 而非 " - "
 * 示例：2026-07-19 21:33:35.138  INFO [main] c.l.l.LogServiceApplication : Starting...
 */
public class LogbackDefaultParser implements LogParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\S+\\s+\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+(\\S+)\\s+:\\s+(.*)$"
    );

    private static final DateTimeFormatter[] FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    @Override
    public LogEntry parse(String fileName, String line) {
        Matcher m = PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }
        return LogEntry.builder()
                .fileName(fileName)
                .logTime(parseTimestamp(m.group(1)))
                .logLevel(m.group(2))
                .threadName(m.group(3))
                .className(m.group(4))
                .content(m.group(5))
                .build();
    }

    @Override
    public boolean matches(String sampleLine) {
        return PATTERN.matcher(sampleLine).matches();
    }

    @Override
    public String name() {
        return "LogbackDefault";
    }

    private LocalDateTime parseTimestamp(String ts) {
        for (DateTimeFormatter fmt : FORMATS) {
            try {
                return LocalDateTime.parse(ts, fmt);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }
}
