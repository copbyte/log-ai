package com.logmonitor.alert.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.alert.service.AlertRecordService;
import com.logmonitor.alert.service.AlertRuleService;
import com.logmonitor.alert.service.NotificationService;
import com.logmonitor.alert.websocket.AlertWebSocketHandler;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告警检查消息消费者
 * <p>
 * 消费失败时由Spring Retry拦截器自动重试（最多3次，指数退避：1s→2s→4s），
 * 重试耗尽后消息进入死信队列，由DlxMessageHandler统一处理。
 * 消费者自身不捕获异常，所有异常上抛给容器层面的重试拦截器。
 */
@Slf4j
@Component
public class LogEntryConsumer {

    private final AlertRuleService alertRuleService;
    private final AlertRecordService alertRecordService;
    private final NotificationService notificationService;
    private final AlertWebSocketHandler webSocketHandler;

    public LogEntryConsumer(AlertRuleService alertRuleService,
                            AlertRecordService alertRecordService,
                            NotificationService notificationService,
                            AlertWebSocketHandler webSocketHandler) {
        this.alertRuleService = alertRuleService;
        this.alertRecordService = alertRecordService;
        this.notificationService = notificationService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 消费日志条目并执行告警规则匹配
     * <p>
     * 使用手动确认模式（acknowledge-mode: manual），处理成功后显式ack。
     * 任何异常上抛给Spring Retry拦截器处理，避免消费者手动nack导致的无限requeue循环。
     * 注意：如果部分规则已触发告警后发生异常，重试可能导致重复告警。
     * 建议在AlertRecordService中通过唯一索引（ruleId + logEntryId）做幂等去重。
     */
    @RabbitListener(queues = "log.monitor.alert.queue")
    public void onMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        LogEntry logEntry = JSON.parseObject(message, LogEntry.class);
        log.debug("Received log entry for alert check: id={}", logEntry.getId());

        List<AlertRule> rules = alertRuleService.listEnabled();
        for (AlertRule rule : rules) {
            if (alertRuleService.matchRule(rule, logEntry)) {
                AlertRecord record = AlertRecord.builder()
                        .ruleId(rule.getId())
                        .logEntryId(logEntry.getId())
                        .alertContent(String.format("Rule [%s] matched: %s [%s] %s",
                                rule.getRuleName(), logEntry.getFileName(),
                                logEntry.getLogLevel(), logEntry.getContent()))
                        .notifyStatus("PENDING")
                        .build();

                alertRecordService.save(record);
                notificationService.sendAlert(rule, record);
                webSocketHandler.broadcastAlert(record);

                log.info("Alert triggered: rule={}, recordId={}", rule.getRuleName(), record.getId());
            }
        }

        channel.basicAck(tag, false);
    }
}
