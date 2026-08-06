package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准 Syslog 解析器（RFC 3164）
 * <p>
 * 格式：&lt;PRI&gt;MMM dd HH:mm:ss hostname process: message
 * 示例：&lt;134&gt;Oct 15 14:32:01 192.168.1.1 firewall: deny src=10.0.0.5 dst=192.168.1.100 dst_port=22 protocol=TCP
 * <p>
 * PRI = facility*8 + severity
 * syslog severity: 0=Emergency 1=Alert 2=Critical 3=Error 4=Warning 5=Notice 6=Info 7=Debug
 */
public class SyslogParser implements LogParser {

    // <134>Oct 15 14:32:01 192.168.1.1 firewall: deny src=10.0.0.5 ...
    private static final Pattern PATTERN = Pattern.compile(
            "^<(\\d{1,3})>(\\w{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(\\S+?):\\s*(.*)$"
    );

    // 消息体中提取 IP/端口/协议/动作
    private static final Pattern SRC_IP = Pattern.compile("src=(\\S+)");
    private static final Pattern DST_IP = Pattern.compile("dst=(\\S+)");
    private static final Pattern SRC_PORT = Pattern.compile("src_port=(\\d+)");
    private static final Pattern DST_PORT = Pattern.compile("dst_port=(\\d+)");
    private static final Pattern PROTOCOL = Pattern.compile("protocol=(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    @Override
    public LogEntry parse(String fileName, String line) {
        Matcher m = PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }
        int pri = Integer.parseInt(m.group(1));
        int syslogSeverity = pri & 0x07; // 低 3 位是 severity
        String hostname = m.group(3);
        String process = m.group(4);
        String message = m.group(5);

        String logLevel = mapSeverity(syslogSeverity);

        LogEntry entry = LogEntry.builder()
                .fileName(fileName != null ? fileName : "syslog")
                .logTime(parseTimestamp(m.group(2)))
                .logLevel(logLevel)
                .className(process)
                .content(message)
                .serviceName(process)
                .logSource("SYSLOG")
                .severity(syslogSeverityToSa(syslogSeverity))
                .build();

        // 从消息体提取安全字段
        extractField(SRC_IP, message).ifPresent(entry::setSrcIp);
        extractField(DST_IP, message).ifPresent(entry::setDstIp);
        extractField(SRC_PORT, message).ifPresent(p -> entry.setSrcPort(Integer.parseInt(p)));
        extractField(DST_PORT, message).ifPresent(p -> entry.setDstPort(Integer.parseInt(p)));
        extractField(PROTOCOL, message).ifPresent(entry::setProtocol);

        // 动作从消息体开头或 action= 字段提取
        String action = extractAction(message);
        if (action != null) {
            entry.setAction(action);
        }

        return entry;
    }

    @Override
    public boolean matches(String sampleLine) {
        return PATTERN.matcher(sampleLine).matches();
    }

    @Override
    public String name() {
        return "Syslog";
    }

    /** syslog severity 映射到日志级别 */
    private String mapSeverity(int severity) {
        switch (severity) {
            case 0: case 1: case 2: return "ERROR";
            case 3: case 4: return "WARN";
            case 5: case 6: return "INFO";
            case 7: return "DEBUG";
            default: return "INFO";
        }
    }

    /** syslog severity 映射到态势感知 0-10 严重级别 */
    private int syslogSeverityToSa(int severity) {
        // syslog 0-7 映射到 SA 0-10：0=Emergency→10, 7=Debug→1
        return Math.max(1, 10 - severity);
    }

    private LocalDateTime parseTimestamp(String ts) {
        try {
            return LocalDateTime.parse(ts + " " + Year.now().getValue(), TIME_FMT);
        } catch (Exception e) {
            return LocalDateTime.now(ZoneId.systemDefault());
        }
    }

    private java.util.Optional<String> extractField(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (m.find()) {
            return java.util.Optional.of(m.group(1));
        }
        return java.util.Optional.empty();
    }

    /** 从消息体提取动作：deny/allow/blocked/drop */
    private String extractAction(String message) {
        String lower = message.toLowerCase();
        if (lower.startsWith("deny") || lower.startsWith("blocked") || lower.startsWith("drop")) {
            return lower.split("\\s+")[0];
        }
        if (lower.startsWith("allow") || lower.startsWith("permit") || lower.startsWith("pass")) {
            return "allow";
        }
        // 尝试 action=xxx 字段
        Matcher m = Pattern.compile("action=(\\S+)", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        return null;
    }
}
