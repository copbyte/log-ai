package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志批处理异步处理器
 * <p>
 * v2.0 改造后已移除 WebSocket 广播和 RabbitMQ 消息发送逻辑，
 * 批量入库由 FileWatchService 直接调用 LogEntryService.saveBatch 完成。
 * 保留此类与异步线程池，便于后续扩展批处理逻辑。
 */
@Slf4j
@Component
public class LogBatchProcessor {

    /**
     * 异步批量处理日志条目
     * <p>
     * 在独立的线程池中执行，不阻塞主轮询线程的下一次文件扫描。
     *
     * @param entries 已持久化的日志条目列表
     */
    @Async("logBatchExecutor")
    public void processBatchAsync(List<LogEntry> entries) {
        // v2.0 改造：WebSocket 推送与 RabbitMQ 分发已移除
    }
}
