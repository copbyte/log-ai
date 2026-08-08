package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ES 健康指示器测试：未启用视为 UP、ping 成功 UP、ping 失败 DOWN。
 */
class EsHealthIndicatorTest {

    private EsProperties properties;
    private ElasticsearchClient esClient;
    private EsHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        properties = new EsProperties();
        properties.setEnabled(true);
        properties.setUris("http://localhost:9200");
        esClient = mock(ElasticsearchClient.class);
        indicator = new EsHealthIndicator(properties, () -> esClient);
    }

    @Test
    void disabledIsUp() {
        properties.setEnabled(false);
        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void pingOkIsUp() throws Exception {
        when(esClient.ping()).thenReturn(new BooleanResponse(true));
        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void pingFailIsDown() throws Exception {
        when(esClient.ping()).thenReturn(new BooleanResponse(false));
        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void pingErrorIsDown() throws Exception {
        when(esClient.ping()).thenThrow(new RuntimeException("es down"));
        Health health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }
}
