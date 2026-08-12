package com.logmonitor.log.pipeline;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.service.impl.LogBatchProcessor;
import com.logmonitor.log.storage.EsLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 批量消费端：消费日志主题 → 解析 → 批量入库 → 触发批次后置处理
 * <p>
 * 语义：至少一次（at-least-once）。入库成功后才手动 ack；入库失败不 ack，
 * 消息会被 Kafka 重新投递，可能产生少量重复入库（后续可加唯一键做幂等兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.pipeline.mode", havingValue = "kafka")
public class KafkaLogConsumer {

    private final LogEntryService logEntryService;
    private final LogBatchProcessor logBatchProcessor;
    private final LogPipelineProperties properties;
    private final EsLogWriter esLogWriter;

    @KafkaListener(
            topics = "${log.pipeline.topic:log-entry}",
            groupId = "${log.pipeline.group-id:log-service-consumer}",
            concurrency = "${log.pipeline.concurrency:1}")
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            for (ConsumerRecord<String, String> record : records) {
                // 逐条处理而不是先汇总全部记录，避免单次 poll 数据量过大导致内存峰值
                saveInChunks(parse(record.value()));
            }
            // 全部入库成功才 ack；失败则交给 Kafka 重新投递
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Kafka 日志消费失败，消息将重新投递: {}", e.getMessage(), e);
        }
    }

    /** 解析消息体（JSON 数组）；坏消息记日志跳过，避免阻塞整批 */
    private List<LogEntry> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(payload, LogEntry.class);
        } catch (JSONException e) {
            log.error("Kafka 消息 JSON 解析失败，跳过该消息: {}", truncate(payload, 200));
            return List.of();
        }
    }

    /** 按 batchSize 分片批量入库，控制单次批量大小 */
    private void saveInChunks(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<LogEntry> chunk = new ArrayList<>(properties.getBatchSize());
        for (LogEntry entry : entries) {
            chunk.add(entry);
            if (chunk.size() >= properties.getBatchSize()) {
                flush(chunk);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            flush(chunk);
        }
    }

    private void flush(List<LogEntry> chunk) {
        // 传副本：后续 chunk 会被 clear 复用，异步处理器/调用方不应持有可变引用
        List<LogEntry> snapshot = new ArrayList<>(chunk);
        logEntryService.saveBatch(snapshot, snapshot.size());
        // 双写：ES 失败不影响 MySQL（EsLogWriter 内部 fail-safe）
        esLogWriter.write(snapshot);
        logBatchProcessor.processBatchAsync(snapshot);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
