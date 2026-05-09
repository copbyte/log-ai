package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mq.producer.LogEntryProducer;
import com.logmonitor.log.websocket.LogWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志批处理异步处理器
 * <p>
 * 将WebSocket广播和RabbitMQ发送与主轮询线程解耦，
 * 主线程仅负责文件读取和数据库批量写入，后续IO操作由此异步执行。
 * 即使广播或消息发送失败，也不影响已持久化的日志数据。
 */
@Slf4j
@Component
public class LogBatchProcessor {

    private final LogWebSocketHandler webSocketHandler;
    private final LogEntryProducer logEntryProducer;

    public LogBatchProcessor(LogWebSocketHandler webSocketHandler,
                             LogEntryProducer logEntryProducer) {
        this.webSocketHandler = webSocketHandler;
        this.logEntryProducer = logEntryProducer;
    }

    /**
     * 异步批量处理WebSocket广播和RabbitMQ消息发送
     * <p>
     * 在独立的线程池中执行，不阻塞主轮询线程的下一次文件扫描。
     * 逐条处理以保留原有的异常隔离逻辑——单条失败不影响同批次其他条目。
     *
     * @param entries 已持久化的日志条目列表
     */
    @Async("logBatchExecutor")
    public void processBatchAsync(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            try {
                webSocketHandler.broadcastLogEntry(entry);
            } catch (Exception e) {
                log.warn("WebSocket广播失败: entryId={}", entry.getId(), e);
            }
            try {
                logEntryProducer.send(entry);
            } catch (Exception e) {
                log.warn("RabbitMQ发送失败: entryId={}", entry.getId(), e);
            }
        }
        log.debug("异步批处理完成，处理{}条日志", entries.size());
    }
}
