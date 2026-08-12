package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.pipeline.LogPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 模拟安全日志生成器（面试演示用）
 * <p>
 * 定时产生各类安全事件日志（防火墙拒绝、端口扫描、SQL注入、XSS攻击、暴力破解等），
 * 让态势感知大屏有数据可看。生产环境通过 log.simulator.enabled=false 关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.simulator.enabled", havingValue = "true")
public class SecurityLogSimulator {

    private final LogPipeline logPipeline;

    private final Random random = new Random();

    /** 恶意源 IP 池 */
    private static final String[] MALICIOUS_IPS = {
            "10.0.0.5", "10.0.0.6", "192.168.100.50", "172.16.0.99", "45.13.22.8"
    };

    /** 目标 IP 池 */
    private static final String[] TARGET_IPS = {
            "192.168.1.100", "192.168.1.101", "192.168.1.102"
    };

    /** 常见被扫描端口 */
    private static final int[] COMMON_PORTS = {22, 3389, 445, 80, 443, 3306, 8080};

    /** 每 5 秒生成一批日志（2-5 条） */
    @Scheduled(fixedDelay = 5000)
    public void generate() {
        int count = 2 + random.nextInt(4); // 2-5 条
        List<LogEntry> batch = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            LogEntry entry = generateOne();
            if (entry != null) {
                batch.add(entry);
            }
        }

        if (!batch.isEmpty()) {
            try {
                logPipeline.persist(batch);
                log.info("模拟安全日志生成 {} 条", batch.size());
            } catch (Exception e) {
                log.error("模拟安全日志管道写入失败: {}", e.getMessage());
            }
        }
    }

    /** 按概率随机生成一条安全事件日志 */
    private LogEntry generateOne() {
        int roll = random.nextInt(100);
        if (roll < 30) {
            return buildFirewallDeny();
        } else if (roll < 50) {
            return buildPortScan();
        } else if (roll < 65) {
            return buildSqlInjection();
        } else if (roll < 80) {
            return buildXssAttack();
        } else {
            return buildBruteForce();
        }
    }

    /** 防火墙拒绝（30%） */
    private LogEntry buildFirewallDeny() {
        String srcIp = pick(MALICIOUS_IPS);
        String dstIp = pick(TARGET_IPS);
        int dstPort = pickPort();
        int severity = 6 + random.nextInt(2); // 6-7
        return baseBuilder()
                .serviceName("firewall")
                .srcIp(srcIp)
                .dstIp(dstIp)
                .srcPort(ephemeralPort())
                .dstPort(dstPort)
                .protocol("TCP")
                .action("deny")
                .severity(severity)
                .logLevel(mapLevel(severity))
                .content("Firewall deny: port scan detected from " + srcIp + " to " + dstIp + ":" + dstPort)
                .build();
    }

    /** 端口扫描（20%） */
    private LogEntry buildPortScan() {
        String srcIp = pick(MALICIOUS_IPS);
        String dstIp = pick(TARGET_IPS);
        int dstPort = pickPort();
        int severity = 6;
        return baseBuilder()
                .serviceName("ids")
                .srcIp(srcIp)
                .dstIp(dstIp)
                .srcPort(ephemeralPort())
                .dstPort(dstPort)
                .protocol("TCP")
                .action("deny")
                .severity(severity)
                .logLevel(mapLevel(severity))
                .content("Port scan detected from " + srcIp + " targeting " + dstIp + ":" + dstPort)
                .build();
    }

    /** SQL注入（15%） */
    private LogEntry buildSqlInjection() {
        String srcIp = pick(MALICIOUS_IPS);
        String dstIp = pick(TARGET_IPS);
        int dstPort = pickPort();
        int severity = 8;
        String[] payloads = {
                "WAF blocked: SQL injection attempt: ' OR 1=1 UNION SELECT * FROM users--",
                "WAF blocked: SQL injection attempt: union select password from mysql.user",
                "WAF blocked: SQL injection attempt: '; DROP TABLE users; --"
        };
        return baseBuilder()
                .serviceName("waf")
                .srcIp(srcIp)
                .dstIp(dstIp)
                .srcPort(ephemeralPort())
                .dstPort(dstPort)
                .protocol("HTTP")
                .action("blocked")
                .severity(severity)
                .logLevel(mapLevel(severity))
                .content(pick(payloads))
                .build();
    }

    /** XSS攻击（15%） */
    private LogEntry buildXssAttack() {
        String srcIp = pick(MALICIOUS_IPS);
        String dstIp = pick(TARGET_IPS);
        int dstPort = pickPort();
        int severity = 8;
        String[] payloads = {
                "WAF blocked: XSS attempt: <script>alert('xss')</script>",
                "WAF blocked: XSS attempt: <script>document.cookie</script>",
                "WAF blocked: XSS attempt: <img src=x onerror=alert(1)>"
        };
        return baseBuilder()
                .serviceName("waf")
                .srcIp(srcIp)
                .dstIp(dstIp)
                .srcPort(ephemeralPort())
                .dstPort(dstPort)
                .protocol("HTTP")
                .action("blocked")
                .severity(severity)
                .logLevel(mapLevel(severity))
                .content(pick(payloads))
                .build();
    }

    /** 暴力破解（20%） */
    private LogEntry buildBruteForce() {
        String srcIp = pick(MALICIOUS_IPS);
        String dstIp = pick(TARGET_IPS);
        int dstPort = pickPort();
        int severity = 7;
        int failed = 3 + random.nextInt(8); // 3-10 次失败
        return baseBuilder()
                .serviceName("ids")
                .srcIp(srcIp)
                .dstIp(dstIp)
                .srcPort(ephemeralPort())
                .dstPort(dstPort)
                .protocol("TCP")
                .action("deny")
                .severity(severity)
                .logLevel(mapLevel(severity))
                .content("Brute force attempt: " + failed + " failed logins from " + srcIp + " to " + dstIp + ":" + dstPort)
                .build();
    }

    /** 公共字段构建器 */
    private LogEntry.LogEntryBuilder baseBuilder() {
        return LogEntry.builder()
                .logTime(LocalDateTime.now())
                .logSource("SYSLOG")
                .fileName("simulated-security.log")
                .className("SecuritySimulator")
                .threadName("sim-thread");
    }

    /** severity 映射日志级别：>=8→ERROR，5-7→WARN，其他→INFO */
    private String mapLevel(int severity) {
        if (severity >= 8) {
            return "ERROR";
        } else if (severity >= 5) {
            return "WARN";
        }
        return "INFO";
    }

    /** 从数组随机取一个元素 */
    private <T> T pick(T[] arr) {
        return arr[random.nextInt(arr.length)];
    }

    /** 随机取一个常用端口 */
    private int pickPort() {
        return COMMON_PORTS[random.nextInt(COMMON_PORTS.length)];
    }

    /** 随机生成一个临时源端口（1024-65535） */
    private int ephemeralPort() {
        return 1024 + random.nextInt(64512);
    }
}
