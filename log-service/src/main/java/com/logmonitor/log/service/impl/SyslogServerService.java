package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.parser.CEFParser;
import com.logmonitor.log.parser.LogParser;
import com.logmonitor.log.parser.SyslogParser;
import com.logmonitor.log.pipeline.LogPipeline;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * UDP Syslog 采集器
 * <p>
 * 监听 UDP 端口（默认 5140），接收防火墙/IDS 等网络设备发送的 syslog 日志，
 * 解析后通过日志管道（Kafka/直连）落库。用有界队列缓冲，防止 OOM。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.syslog.enabled", havingValue = "true")
public class SyslogServerService {

    private final LogPipeline logPipeline;

    @Value("${log.syslog.port:5140}")
    private int port;

    @Value("${log.syslog.buffer-size:5000}")
    private int bufferSize;

    private final LogParser syslogParser = new SyslogParser();
    private final LogParser cefParser = new CEFParser();

    private DatagramSocket socket;
    private Thread listenerThread;
    private Thread consumerThread;
    private volatile boolean running = true;

    /** 有界缓冲队列，防止 OOM */
    private BlockingQueue<LogEntry> buffer;

    @PostConstruct
    public void start() {
        buffer = new ArrayBlockingQueue<>(bufferSize);
        try {
            socket = new DatagramSocket(port);
            log.info("Syslog 采集器启动，监听 UDP 端口: {}", port);

            // 接收线程
            listenerThread = new Thread(this::listen, "syslog-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // 消费线程（批量落库）
            consumerThread = new Thread(this::consume, "syslog-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        } catch (Exception e) {
            log.error("Syslog 采集器启动失败，端口 {} 可能被占用: {}", port, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        log.info("Syslog 采集器已停止");
    }

    /** 接收 UDP 数据包并解析 */
    private void listen() {
        byte[] buf = new byte[8192];
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                InetAddress source = packet.getAddress();
                LogEntry entry = parse(raw);
                if (entry != null) {
                    // 如果日志没有源 IP，用 UDP 包来源地址填充
                    if (entry.getSrcIp() == null) {
                        entry.setSrcIp(source.getHostAddress());
                    }
                    // 队列满时丢弃，记 WARN
                    if (!buffer.offer(entry)) {
                        log.warn("Syslog 缓冲队列已满（size={}），丢弃日志: {}", bufferSize, raw.substring(0, Math.min(raw.length(), 100)));
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Syslog 接收异常: {}", e.getMessage());
                }
            }
        }
    }

    /** 消费队列，批量管道落库；失败放回队列重试 */
    private void consume() {
        List<LogEntry> batch = new ArrayList<>(500);
        while (running || !buffer.isEmpty()) {
            try {
                // 阻塞取第一条
                LogEntry first = buffer.poll(200, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    // 非阻塞取剩余，最多 500 条
                    buffer.drainTo(batch, 499);
                }
                if (!batch.isEmpty()) {
                    try {
                        logPipeline.persist(batch);
                        log.debug("Syslog 管道落库 {} 条", batch.size());
                    } catch (Exception e) {
                        log.error("Syslog 管道写入失败，放回队列重试: {}", e.getMessage());
                        requeue(batch);
                    } finally {
                        batch.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Syslog 消费异常: {}", e.getMessage());
                batch.clear();
            }
        }
    }

    /** 写入失败时放回缓冲队列，下次消费重试（队列满则丢弃并告警） */
    private void requeue(List<LogEntry> batch) {
        for (LogEntry entry : batch) {
            if (!buffer.offer(entry)) {
                log.warn("Syslog 重试队列已满，丢弃日志: {}", entry.getContent());
                break;
            }
        }
    }

    /** 解析 syslog 报文：优先 CEF，再标准 syslog */
    private LogEntry parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        // CEF 格式优先
        if (cefParser.matches(raw)) {
            return cefParser.parse("syslog", raw);
        }
        // 标准 syslog
        if (syslogParser.matches(raw)) {
            return syslogParser.parse("syslog", raw);
        }
        // 无法解析的裸消息，兜底处理
        return LogEntry.builder()
                .fileName("syslog")
                .logLevel("INFO")
                .content(raw)
                .logSource("SYSLOG")
                .serviceName("unknown")
                .build();
    }
}
