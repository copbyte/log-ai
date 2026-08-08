package com.logmonitor.log.health;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 健康指示器：通过 AdminClient 查询集群 ID 判断 broker 是否可用。
 * <p>
 * Spring Boot 3.3 没有内置 Kafka 健康检查（不像 db/redis/es），
 * 而就绪探针需要 Kafka 状态（采集链路依赖），因此自研一个。
 * 使用懒加载共享 AdminClient，避免每次探针都创建连接。
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final long TIMEOUT_SECONDS = 3;

    private final String bootstrapServers;
    private final AdminClient injectedAdmin;
    private volatile AdminClient adminClient;

    @Autowired
    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this(bootstrapServers, null);
    }

    /** 包级可见：测试注入 mock AdminClient */
    KafkaHealthIndicator(String bootstrapServers, AdminClient injectedAdmin) {
        this.bootstrapServers = bootstrapServers;
        this.injectedAdmin = injectedAdmin;
    }

    @Override
    public Health health() {
        try {
            String clusterId = adminClient()
                    .describeCluster()
                    .clusterId()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (clusterId == null || clusterId.isBlank()) {
                return Health.down()
                        .withDetail("bootstrapServers", bootstrapServers)
                        .build();
            }
            return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();
        }
    }

    private AdminClient adminClient() {
        if (injectedAdmin != null) {
            return injectedAdmin;
        }
        if (adminClient == null) {
            synchronized (this) {
                if (adminClient == null) {
                    Map<String, Object> props = new HashMap<>();
                    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
                    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
                    adminClient = AdminClient.create(props);
                }
            }
        }
        return adminClient;
    }

    @PreDestroy
    public void close() {
        if (adminClient != null) {
            adminClient.close();
        }
    }
}
