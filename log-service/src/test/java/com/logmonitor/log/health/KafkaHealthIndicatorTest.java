package com.logmonitor.log.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka 健康指示器测试：集群可达 UP、超时 DOWN、空集群 ID DOWN。
 */
class KafkaHealthIndicatorTest {

    @Test
    void clusterReachableIsUp() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        DescribeClusterResult result = mock(DescribeClusterResult.class);
        when(admin.describeCluster()).thenReturn(result);
        when(result.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-1"));
        KafkaHealthIndicator indicator = new KafkaHealthIndicator("localhost:9092", admin);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void clusterTimeoutIsDown() {
        AdminClient admin = mock(AdminClient.class);
        when(admin.describeCluster()).thenThrow(new TimeoutException("broker timeout"));
        KafkaHealthIndicator indicator = new KafkaHealthIndicator("localhost:9092", admin);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void emptyClusterIdIsDown() {
        AdminClient admin = mock(AdminClient.class);
        DescribeClusterResult result = mock(DescribeClusterResult.class);
        when(admin.describeCluster()).thenReturn(result);
        when(result.clusterId()).thenReturn(KafkaFuture.completedFuture(null));
        KafkaHealthIndicator indicator = new KafkaHealthIndicator("localhost:9092", admin);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
