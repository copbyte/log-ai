package com.logmonitor.log.storage;

import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * ES 健康指示器：就绪探针会检查 ES 连通性。
 * <p>
 * ES 未开启时视为 UP（MySQL 兜底模式仍可服务）；
 * 开启后 ping 失败则 DOWN，K8s readinessProbe 会停止分发流量。
 */
@Component
public class EsHealthIndicator implements HealthIndicator {

    private final EsProperties properties;
    private final EsClientProvider clientProvider;

    public EsHealthIndicator(EsProperties properties, EsClientProvider clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        try {
            BooleanResponse response = clientProvider.getClient().ping();
            if (response.value()) {
                return Health.up()
                        .withDetail("enabled", true)
                        .withDetail("uri", properties.getUris())
                        .build();
            }
            return Health.down().withDetail("enabled", true).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("enabled", true).build();
        }
    }
}
