package com.logmonitor.log.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.DlxMessage;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.DlxMessageMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 死信队列消息处理器
 * <p>
 * 消费进入死信队列的消息，将其持久化到专用的 dlx_message 表，
 * 防止消费失败的消息永久丢失。运维人员可通过查询此表排查问题。
 * <p>
 * 表结构设计：
 * - original_body: 原始JSON，完整保留消息内容供排查
 * - origin_queue: 来源队列，区分是AI分析失败还是告警检查失败
 * - fail_reason: 失败原因摘要
 * - retry_count: 重试次数（由消费者Retry拦截器耗尽后进入DLQ，默认3次）
 * - status: 处理状态（PENDING待处理/RESOLVED已处理/IGNORED已忽略）
 * - log_entry_id + log_content: 可解析时关联原始日志
 */
@Slf4j
@Component
public class DlxMessageHandler {

    private final DlxMessageMapper dlxMessageMapper;

    public DlxMessageHandler(DlxMessageMapper dlxMessageMapper) {
        this.dlxMessageMapper = dlxMessageMapper;
    }

    /**
     * 监听死信队列，将失败消息持久化到 dlx_message 表
     * <p>
     * 尝试解析消息中的LogEntry信息用于关联原始日志，
     * 解析失败也不影响持久化——original_body完整保留原始内容。
     *
     * @param message 死信消息的JSON字符串
     * @param channel RabbitMQ Channel
     * @param tag     投递标签
     */
    @RabbitListener(queues = "log.monitor.dlx.queue")
    public void handleDlxMessage(String message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag,
                                  @Header(value = AmqpHeaders.CONSUMER_QUEUE, required = false) String originQueue) {
        log.warn("收到死信消息，来源队列: {}, 内容长度: {} 字符", originQueue, message.length());

        // 提取失败原因：从header或消息体中获取（如果有的话）
        String failReason = extractFailReason(message);

        // 尝试解析为LogEntry以关联原始日志
        Long logEntryId = null;
        String logContent = null;
        try {
            LogEntry logEntry = JSON.parseObject(message, LogEntry.class);
            if (logEntry != null) {
                logEntryId = logEntry.getId();
                logContent = logEntry.getContent();
            }
        } catch (Exception e) {
            // 解析失败不影响死信消息的保存，original_body保留完整原始内容
            log.debug("无法将死信消息解析为LogEntry，将完整保留原始内容");
        }

        try {
            DlxMessage dlxMsg = DlxMessage.builder()
                    .originalBody(message)
                    .originQueue(originQueue != null ? originQueue : "unknown")
                    .failReason(failReason)
                    .retryCount(3) // 消费者Retry拦截器默认重试3次
                    .status("PENDING")
                    .logEntryId(logEntryId)
                    .logContent(logContent)
                    .build();

            dlxMessageMapper.insert(dlxMsg);
            log.warn("死信消息已保存到dlx_message表: id={}, originQueue={}", dlxMsg.getId(), dlxMsg.getOriginQueue());

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("保存死信消息失败，原始内容: {}", message, e);
            try {
                // 保存失败也确认消费，避免死信队列中积压无法处理的消息
                channel.basicAck(tag, false);
            } catch (Exception ex) {
                log.error("确认死信消息失败", ex);
            }
        }
    }

    /**
     * 从消息体中提取失败原因
     * <p>
     * 这里做简单的关键词提取，实际生产环境可以通过消息头传递异常信息。
     */
    private String extractFailReason(String message) {
        if (message == null || message.isEmpty()) {
            return "消息体为空";
        }
        // 限定长度避免字段溢出（数据库fail_reason为VARCHAR(500)）
        if (message.length() <= 500) {
            return message;
        }
        return message.substring(0, 497) + "...";
    }
}
