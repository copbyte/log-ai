package com.logmonitor.starter;

import com.logmonitor.common.entity.LogEntry;

/**
 * 日志监控客户端接口
 * <p>
 * 两种实现通过配置切换：
 * <ul>
 *   <li>{@code log-monitor.mode: http}（默认）— HTTP 调用 log-service，无需 RabbitMQ</li>
 *   <li>{@code log-monitor.mode: mq} — 直连 RabbitMQ Exchange，性能更高但需配置 spring.rabbitmq.*</li>
 * </ul>
 */
public interface LogMonitorClient {

    /**
     * 发送单条日志到监控平台
     */
    void send(LogEntry logEntry);

    /**
     * 刷新缓冲区，确保积攒的日志不丢失
     */
    void flush();
}
