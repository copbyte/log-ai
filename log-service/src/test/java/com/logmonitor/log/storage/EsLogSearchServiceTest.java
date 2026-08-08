package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

/**
 * ES 查询服务测试：开关关闭返回 null、命中映射为分页结果、ES 异常返回 null（触发 MySQL 兜底）。
 */
class EsLogSearchServiceTest {

    private EsProperties properties;
    private ElasticsearchClient esClient;
    private EsLogSearchService service;

    @BeforeEach
    void setUp() {
        properties = new EsProperties();
        properties.setEnabled(true);
        properties.setUris("http://localhost:9200");
        properties.setIndexPrefix("log-entry");
        esClient = mock(ElasticsearchClient.class);
        service = new EsLogSearchService(properties, () -> esClient);
    }

    @Test
    void disabledReturnsNull() {
        properties.setEnabled(false);
        assertNull(service.search(new Page<>(1, 20), null, null, null, null,
                null, null, null, null, null, null, null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchMapsHitsToPage() throws Exception {
        Hit<JsonData> hit = Hit.<JsonData>of(h -> h
                .index("log-entry-2026-08-07")
                .id("1")
                .source(JsonData.fromJson(
                        "{\"id\":1,\"logLevel\":\"ERROR\",\"logTime\":\"2026-08-07T10:00:00\","
                                + "\"content\":\"boom\",\"serviceName\":\"order-service\"}")));
        SearchResponse<JsonData> response = SearchResponse.of(b -> b
                .took(3L)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).skipped(0).failed(0))
                .hits(h -> h
                        .total(t -> t.value(1).relation(TotalHitsRelation.Eq))
                        .hits(List.of(hit))));
        when(esClient.search(any(SearchRequest.class),
                ArgumentMatchers.<Class<JsonData>>any())).thenReturn(response);

        Page<LogEntry> result = service.search(new Page<>(1, 20), "ERROR", null, null, null,
                null, null, "boom", null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("ERROR", result.getRecords().get(0).getLogLevel());
        assertEquals("boom", result.getRecords().get(0).getContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void esFailureReturnsNullForFallback() throws Exception {
        when(esClient.search(any(SearchRequest.class),
                ArgumentMatchers.<Class<JsonData>>any()))
                .thenThrow(new RuntimeException("es down"));

        assertNull(service.search(new Page<>(1, 20), null, null, null, null,
                null, null, null, null, null, null, null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByTraceIdMapsHits() throws Exception {
        Hit<JsonData> hit = Hit.<JsonData>of(h -> h
                .index("log-entry-2026-08-07")
                .id("1")
                .source(JsonData.fromJson(
                        "{\"id\":1,\"traceId\":\"trace-001\",\"logTime\":\"2026-08-07T10:00:00\","
                                + "\"content\":\"ok\"}")));
        SearchResponse<JsonData> response = SearchResponse.of(b -> b
                .took(3L)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).skipped(0).failed(0))
                .hits(h -> h
                        .total(t -> t.value(1).relation(TotalHitsRelation.Eq))
                        .hits(List.of(hit))));
        when(esClient.search(any(SearchRequest.class),
                ArgumentMatchers.<Class<JsonData>>any())).thenReturn(response);

        List<LogEntry> logs = service.findByTraceId("trace-001");

        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals("trace-001", logs.get(0).getTraceId());
    }
}
