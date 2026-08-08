package com.logmonitor.log.pipeline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 日志管道配置（log.pipeline.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "log.pipeline")
public class LogPipelineProperties {

    /** 管道模式：kafka（生产）/ direct（直连入库，本地演示降级） */
    private String mode = "direct";

    /** Kafka 主题 */
    private String topic = "log-entry";

    /** 消费组 ID：多实例同组消费时按分区分摊，实现分布式消费与偏移量管理 */
    private String groupId = "log-service-consumer";

    /** 消费端并发线程数（对应 Kafka 分区数，单分区建议保持 1 保证顺序） */
    private int concurrency = 1;

    /** 单次入库批量大小（同时控制单条 Kafka 消息体积） */
    private int batchSize = 500;

    /** Kafka 发送超时：同步等待发送结果，确保文件偏移量只在发送成功后推进 */
    private Duration sendTimeout = Duration.ofSeconds(5);
}
