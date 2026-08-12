package com.logmonitor.log.pipeline;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka 管道测试：验证按批切分发送 JSON、发送失败抛异常。
 */
class KafkaLogPipelineTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaLogPipeline pipeline;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        LogPipelineProperties properties = new LogPipelineProperties();
        properties.setMode("kafka");
        properties.setTopic("log-entry");
        properties.setBatchSize(2);
        properties.setSendTimeout(Duration.ofSeconds(5));
        pipeline = new KafkaLogPipeline(kafkaTemplate, properties);
    }

    @Test
    void persistSendsJsonChunksToTopic() {
        when(kafkaTemplate.send(eq("log-entry"), isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<LogEntry> batch = List.of(
                LogEntry.builder().content("a").build(),
                LogEntry.builder().content("b").build(),
                LogEntry.builder().content("c").build());
        pipeline.persist(batch);

        // batchSize=2 -> 2 条 Kafka 消息（2 条 + 1 条）
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(eq("log-entry"), isNull(), captor.capture());
        assertEquals(2, JSON.parseArray(captor.getAllValues().get(0), LogEntry.class).size());
        assertEquals(1, JSON.parseArray(captor.getAllValues().get(1), LogEntry.class).size());
    }

    @Test
    void persistThrowsWhenSendFails() {
        when(kafkaTemplate.send(eq("log-entry"), isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThrows(Exception.class, () -> pipeline.persist(List.of(LogEntry.builder().build())));
    }
}
