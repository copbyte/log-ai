package com.logmonitor.starter;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * MQ 模式实现 — 直连 RabbitMQ Exchange
 * <p>
 * 优点：异步、零网络跳转、吞吐更高
 * 前提：宿主服务需配置 spring.rabbitmq.* 连接信息
 */
public class MqLogMonitorClient implements LogMonitorClient {

    private final RabbitTemplate rabbitTemplate;
    private final LogMonitorProperties properties;

    public MqLogMonitorClient(RabbitTemplate rabbitTemplate, LogMonitorProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void send(LogEntry logEntry) {
        String json = JSON.toJSONString(logEntry);
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), json);
    }

    @Override
    public void flush() {
        // MQ 模式逐条发送，无需 flush
    }
}
