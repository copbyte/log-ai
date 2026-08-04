package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * log4j2 默认格式解析器
 * <p>
 * 格式：HH:mm:ss.SSS LEVEL [thread] class - message
 * 注意：log4j2 默认时间只有时分秒，不含日期，此处用当前日期补齐
 * 示例：21:33:35.138 INFO [main] c.l.l.LogServiceApplication - Starting...
 */
public class Log4j2DefaultParser implements LogParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+(\\S+)\\s+-\\s+(.*)$"
    );

    @Override
    public LogEntry parse(String fileName, String line) {
        Matcher m = PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }
        // log4j2 默认时间不含日期，用当前日期补齐
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = LocalDateTime.parse(
                now.toLocalDate() + "T" + m.group(1),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        return LogEntry.builder()
                .fileName(fileName)
                .logTime(time)
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
        return "Log4j2Default";
    }
}
