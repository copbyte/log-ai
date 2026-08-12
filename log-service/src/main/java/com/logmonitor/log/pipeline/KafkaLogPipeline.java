package com.logmonitor.log.pipeline;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.entity.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 管道：采集端 → Kafka 主题（削峰缓冲） → 消费端批量入库
 * <p>
 * 采用同步发送（带超时）：只有发送成功才返回，调用方（FileWatchService）才会推进文件偏移量，
 * 保证 Kafka 不可用时日志不丢（偏移量不推进，下一轮轮询重读）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.pipeline.mode", havingValue = "kafka")
public class KafkaLogPipeline implements LogPipeline {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LogPipelineProperties properties;

    @Override
    public void persist(List<LogEntry> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        try {
            // 按配置的批量大小切分，控制单条 Kafka 消息体积
            for (List<LogEntry> chunk : chunk(batch, properties.getBatchSize())) {
                String payload = JSON.toJSONString(chunk);
                kafkaTemplate.send(properties.getTopic(), null, payload)
                        .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            }
            log.debug("Sent {} log entries to kafka topic={}", batch.size(), properties.getTopic());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 发送被中断: " + e.getMessage(), e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Kafka 发送失败: " + e.getMessage(), e);
        }
    }

    private List<List<LogEntry>> chunk(List<LogEntry> batch, int size) {
        List<List<LogEntry>> chunks = new ArrayList<>();
        for (int i = 0; i < batch.size(); i += size) {
            chunks.add(new ArrayList<>(batch.subList(i, Math.min(i + size, batch.size()))));
        }
        return chunks;
    }
}
