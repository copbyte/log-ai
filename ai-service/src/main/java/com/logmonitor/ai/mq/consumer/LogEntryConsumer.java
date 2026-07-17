package com.logmonitor.ai.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.ai.service.AiAnalysisService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * AI分析消息消费者
 * <p>
 * 消费失败时由Spring Retry拦截器自动重试（最多3次，指数退避：1s→2s→4s），
 * 重试耗尽后消息进入死信队列，由DlxMessageHandler统一处理。
 * 消费者自身不捕获异常，所有异常上抛给容器层面的重试拦截器。
 */
@Component
public class LogEntryConsumer {

    private final AiAnalysisService aiAnalysisService;
    
    @Value("${mq.consumer.ai.enabled:false}")
    private boolean aiConsumerEnabled;
    
    @Value("${mq.consumer.ai.fake-consume:false}")
    private boolean aiFakeConsume;

    public LogEntryConsumer(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    /**
     * 消费日志条目并执行AI分析
     * <p>
     * 使用手动确认模式（acknowledge-mode: manual），处理成功后显式ack。
     * 任何异常（包括业务异常和IO异常）上抛给Spring Retry拦截器处理，
     * 避免在消费者中手动nack导致的无限制requeue循环。
     */
    @RabbitListener(queues = "log.monitor.ai.queue")
    public void onMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        LogEntry logEntry = JSON.parseObject(message, LogEntry.class);

        if (!aiConsumerEnabled) {
            channel.basicAck(tag, false);
            return;
        }

        if (aiFakeConsume) {
            channel.basicAck(tag, false);
            return;
        }

        aiAnalysisService.analyze(logEntry, false);

        channel.basicAck(tag, false);
    }
}
