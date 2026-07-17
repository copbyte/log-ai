package com.logmonitor.log.mq.producer;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogEntryProducer {

    private static final String EXCHANGE = "log.monitor.log.exchange";
    private static final String ROUTING_KEY = "log.new";

    private final RabbitTemplate rabbitTemplate;

    public LogEntryProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(LogEntry logEntry) {
        String json = JSON.toJSONString(logEntry);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, json);
    }
}
