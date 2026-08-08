package com.logmonitor.log.pipeline;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.service.impl.LogBatchProcessor;
import com.logmonitor.log.storage.EsLogWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Kafka 消费端测试：验证解析、分片入库、手动 ack 与失败不 ack。
 */
class KafkaLogConsumerTest {

    private LogEntryService logEntryService;
    private LogBatchProcessor logBatchProcessor;
    private EsLogWriter esLogWriter;
    private KafkaLogConsumer consumer;

    @BeforeEach
    void setUp() {
        logEntryService = mock(LogEntryService.class);
        logBatchProcessor = mock(LogBatchProcessor.class);
        esLogWriter = mock(EsLogWriter.class);
        LogPipelineProperties properties = new LogPipelineProperties();
        properties.setBatchSize(2);
        consumer = new KafkaLogConsumer(logEntryService, logBatchProcessor, properties, esLogWriter);
    }

    @Test
    void consumeParsesAndSavesInChunksThenAcks() {
        Acknowledgment ack = mock(Acknowledgment.class);
        List<LogEntry> entries = List.of(
                LogEntry.builder().content("1").build(),
                LogEntry.builder().content("2").build(),
                LogEntry.builder().content("3").build());
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("log-entry", 0, 0L, null, JSON.toJSONString(entries));

        consumer.consume(List.of(record), ack);

        // batchSize=2 -> 两次 saveBatch（2 条 + 1 条）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(logEntryService, times(2)).saveBatch(captor.capture(), anyInt());
        assertEquals(2, captor.getAllValues().get(0).size());
        assertEquals(1, captor.getAllValues().get(1).size());
        verify(logBatchProcessor, times(2)).processBatchAsync(any());
        verify(esLogWriter, times(2)).write(any());
        verify(ack).acknowledge();
    }

    @Test
    void consumeSkipsMalformedRecordAndStillAcks() {
        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> bad = new ConsumerRecord<>("log-entry", 0, 0L, null, "not-json{");

        consumer.consume(List.of(bad), ack);

        verify(logEntryService, never()).saveBatch(any(), anyInt());
        verify(ack).acknowledge();
    }

    @Test
    void consumeDoesNotAckWhenSaveFails() {
        Acknowledgment ack = mock(Acknowledgment.class);
        doThrow(new RuntimeException("db down"))
                .when(logEntryService).saveBatch(any(), anyInt());
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "log-entry", 0, 0L, null, JSON.toJSONString(List.of(LogEntry.builder().build())));

        consumer.consume(List.of(record), ack);

        verify(ack, never()).acknowledge();
    }
}
