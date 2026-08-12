package com.logmonitor.log.pipeline;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.service.impl.LogBatchProcessor;
import com.logmonitor.log.storage.EsLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 直连管道：采集端 → 批量入库（无 Kafka 的降级模式，保留原有行为）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.pipeline.mode", havingValue = "direct", matchIfMissing = true)
public class DirectLogPipeline implements LogPipeline {

    private final LogEntryService logEntryService;
    private final LogBatchProcessor logBatchProcessor;
    private final EsLogWriter esLogWriter;

    @Override
    public void persist(List<LogEntry> batch) {
        logEntryService.saveBatch(batch, batch.size());
        // 双写：ES 失败不影响 MySQL（EsLogWriter 内部 fail-safe）
        esLogWriter.write(batch);
        // 批次后置处理（预留扩展点）：当前为空实现，后续 WebSocket 推送 / MQ 分发 / AI 分析在此接入
        logBatchProcessor.processBatchAsync(batch);
    }
}
