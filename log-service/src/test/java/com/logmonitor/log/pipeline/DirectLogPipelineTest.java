package com.logmonitor.log.pipeline;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.service.impl.LogBatchProcessor;
import com.logmonitor.log.storage.EsLogWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * 直连管道测试：验证批量入库与异步后置处理调用。
 */
class DirectLogPipelineTest {

    @Test
    void persistSavesBatchAndTriggersAsyncProcessor() {
        LogEntryService logEntryService = mock(LogEntryService.class);
        LogBatchProcessor logBatchProcessor = mock(LogBatchProcessor.class);
        EsLogWriter esLogWriter = mock(EsLogWriter.class);
        DirectLogPipeline pipeline = new DirectLogPipeline(logEntryService, logBatchProcessor, esLogWriter);

        List<LogEntry> batch = List.of(LogEntry.builder().content("hello").build());
        pipeline.persist(batch);

        verify(logEntryService).saveBatch(batch, 1);
        verify(esLogWriter).write(batch);
        verify(logBatchProcessor).processBatchAsync(batch);
    }
}
