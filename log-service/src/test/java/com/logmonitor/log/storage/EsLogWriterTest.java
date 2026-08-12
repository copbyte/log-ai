package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.logmonitor.common.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ES 双写测试：开关、按批写入、按天索引、ES 故障不影响主链路。
 */
class EsLogWriterTest {

    private EsProperties properties;
    private ElasticsearchClient esClient;
    private EsLogWriter writer;

    @BeforeEach
    void setUp() {
        properties = new EsProperties();
        properties.setEnabled(true);
        properties.setUris("http://localhost:9200");
        properties.setIndexPrefix("log-entry");
        properties.setBatchSize(2);
        esClient = mock(ElasticsearchClient.class);
        writer = new EsLogWriter(properties, esClient);
    }

    private static LogEntry entry(long id) {
        LogEntry e = LogEntry.builder().content("hello").build();
        e.setId(id);
        return e;
    }

    @Test
    void disabledDoesNothing() throws Exception {
        properties.setEnabled(false);
        writer.write(List.of(entry(1)));
        verifyNoInteractions(esClient);
    }

    @Test
    void writesChunksToDailyIndex() throws Exception {
        when(esClient.bulk(any(BulkRequest.class))).thenReturn(okResponse());

        writer.write(List.of(entry(1), entry(2), entry(3)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(esClient, times(2)).bulk(captor.capture());
        assertEquals(2, captor.getAllValues().get(0).operations().size());
        assertEquals(1, captor.getAllValues().get(1).operations().size());
        // 索引按天，文档 ID 用日志主键
        assertEquals("log-entry-" + LocalDate.now(),
                captor.getAllValues().get(0).operations().get(0).index().index());
        assertEquals("1", captor.getAllValues().get(0).operations().get(0).index().id());
    }

    @Test
    void esFailureDoesNotThrow() throws Exception {
        when(esClient.bulk(any(BulkRequest.class))).thenThrow(new IOException("es down"));
        assertDoesNotThrow(() -> writer.write(List.of(entry(1))));
    }

    private static BulkResponse okResponse() {
        return BulkResponse.of(b -> b.errors(false).took(1L).items(List.of()));
    }
}
