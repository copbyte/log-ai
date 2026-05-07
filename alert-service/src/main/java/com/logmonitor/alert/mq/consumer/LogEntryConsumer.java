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

import java.io.IOException;
import java.util.List;

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

    @RabbitListener(queues = "log.monitor.alert.queue")
    public void onMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
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
        } catch (Exception e) {
            log.error("Failed to process message for alert", e);
            try {
                channel.basicNack(tag, false, true);
            } catch (IOException ioException) {
                log.error("Failed to nack message", ioException);
            }
        }
    }
}
