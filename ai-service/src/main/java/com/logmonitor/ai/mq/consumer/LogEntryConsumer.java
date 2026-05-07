package com.logmonitor.ai.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.ai.service.AiAnalysisService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class LogEntryConsumer {

    private final AiAnalysisService aiAnalysisService;

    public LogEntryConsumer(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @RabbitListener(queues = "log.monitor.ai.queue")
    public void onMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            LogEntry logEntry = JSON.parseObject(message, LogEntry.class);
            log.debug("Received log entry for AI analysis: id={}", logEntry.getId());

            aiAnalysisService.analyze(logEntry);

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message for AI analysis", e);
            try {
                channel.basicNack(tag, false, true);
            } catch (IOException ioException) {
                log.error("Failed to nack message", ioException);
            }
        }
    }
}
