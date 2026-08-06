package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CEF (Common Event Format) 解析器，ArcSight 通用事件格式
 * <p>
 * 格式：CEF:Version|Vendor|Product|Version|SigID|Name|Severity|Extension
 * 示例：CEF:0|Cisco|ASA|1.0|FirewallDeny|Firewall deny|7|src=10.0.0.5 dst=192.168.1.100 spt=12345 dpt=22 act=blocked proto=TCP
 * <p>
 * Severity: 0-10（0=低，10=高）
 * Extension 字段：src/dst/spt/dpt/act/proto 等 key=value 空格分隔
 */
public class CEFParser implements LogParser {

    // CEF:0|Cisco|ASA|1.0|FirewallDeny|Firewall deny|7|src=... dst=...
    private static final Pattern PATTERN = Pattern.compile(
            "^CEF:\\d+\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|(.*)$"
    );

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public LogEntry parse(String fileName, String line) {
        Matcher m = PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String vendor = m.group(1).trim();
        String product = m.group(2).trim();
        String sigId = m.group(4).trim();
        String name = m.group(5).trim();
        String severityStr = m.group(6).trim();
        String extension = m.group(7);

        int severity = parseSeverity(severityStr);
        String logLevel = severityToLogLevel(severity);

        LogEntry entry = LogEntry.builder()
                .fileName(fileName != null ? fileName : "cef")
                .logTime(LocalDateTime.now())
                .logLevel(logLevel)
                .className(product)
                .content(name + " [" + sigId + "]")
                .serviceName(product.isEmpty() ? vendor : product)
                .logSource("CEF")
                .severity(severity)
                .build();

        // 解析扩展字段 key=value
        parseExtension(extension, entry);

        return entry;
    }

    @Override
    public boolean matches(String sampleLine) {
        return sampleLine != null && sampleLine.startsWith("CEF:");
    }

    @Override
    public String name() {
        return "CEF";
    }

    /** 解析扩展字段：src=10.0.0.5 dst=192.168.1.100 spt=12345 dpt=22 act=blocked proto=TCP */
    private void parseExtension(String ext, LogEntry entry) {
        if (ext == null || ext.isEmpty()) {
            return;
        }
        // 按 key=value 模式逐个提取
        putValue(entry, "src", extractValue(ext, "src"));
        putValue(entry, "dst", extractValue(ext, "dst"));
        putValue(entry, "spt", extractValue(ext, "spt"));
        putValue(entry, "dpt", extractValue(ext, "dpt"));
        putValue(entry, "act", extractValue(ext, "act"));
        putValue(entry, "proto", extractValue(ext, "proto"));
    }

    /** 从扩展字符串中提取 key=value 的 value */
    private String extractValue(String ext, String key) {
        // 匹配 key=value，value 到下一个空格或行尾
        Pattern p = Pattern.compile(key + "=(\\S+)");
        Matcher m = p.matcher(ext);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void putValue(LogEntry entry, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        switch (key) {
            case "src": entry.setSrcIp(value); break;
            case "dst": entry.setDstIp(value); break;
            case "spt":
                try { entry.setSrcPort(Integer.parseInt(value)); } catch (Exception ignored) {}
                break;
            case "dpt":
                try { entry.setDstPort(Integer.parseInt(value)); } catch (Exception ignored) {}
                break;
            case "act": entry.setAction(value.toLowerCase()); break;
            case "proto": entry.setProtocol(value.toUpperCase()); break;
        }
    }

    private int parseSeverity(String s) {
        try {
            int v = Integer.parseInt(s);
            // CEF severity 可能是 0-10，也可能是 0-3（低中高极高）
            if (v <= 3) {
                return v * 3; // 0→0, 1→3, 2→6, 3→9
            }
            return Math.min(v, 10);
        } catch (Exception e) {
            return 5;
        }
    }

    private String severityToLogLevel(int severity) {
        if (severity >= 8) return "ERROR";
        if (severity >= 5) return "WARN";
        if (severity >= 3) return "INFO";
        return "DEBUG";
    }
}
