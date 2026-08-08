package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 异步批处理器测试：验证批次入库后委托给流式规则引擎。
 */
class LogBatchProcessorTest {

    @Test
    void processBatchAsyncDelegatesToStreamingRuleEngine() {
        StreamingRuleEngine streamingRuleEngine = mock(StreamingRuleEngine.class);
        LogBatchProcessor processor = new LogBatchProcessor(streamingRuleEngine);
        List<LogEntry> entries = List.of(LogEntry.builder().content("hello").build());

        processor.processBatchAsync(entries);

        verify(streamingRuleEngine).processBatch(entries);
    }
}
