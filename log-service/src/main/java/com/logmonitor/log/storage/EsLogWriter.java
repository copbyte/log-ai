package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ES 日志写入器（双写模式）
 * <p>
 * 在 Kafka 消费端/直连管道入库 MySQL 后调用，把同一批日志 bulk 写入 ES：
 * <ul>
 *   <li>索引按天：{indexPrefix}-2026-08-07，方便按时间清理（ILM/定时删除）；</li>
 *   <li>批量写入（bulk）而非逐条，保证吞吐；</li>
 *   <li>fail-safe：ES 故障只记 WARN，不影响 MySQL 主链路。</li>
 * </ul>
 */
@Slf4j
@Component
public class EsLogWriter {

    private final EsProperties properties;
    private final EsClientProvider clientProvider;

    @Autowired
    public EsLogWriter(EsProperties properties, EsClientProvider clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    /** 包级可见：测试注入 mock client */
    EsLogWriter(EsProperties properties, ElasticsearchClient injectedClient) {
        this.properties = properties;
        this.clientProvider = () -> injectedClient;
    }

    /** 写入一批日志；开关关闭或 ES 异常时静默跳过 */
    public void write(List<LogEntry> entries) {
        if (!properties.isEnabled() || entries == null || entries.isEmpty()) {
            return;
        }
        try {
            ElasticsearchClient es = client();
            List<LogEntry> chunk = new ArrayList<>(properties.getBatchSize());
            for (LogEntry entry : entries) {
                chunk.add(entry);
                if (chunk.size() >= properties.getBatchSize()) {
                    bulk(es, chunk);
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                bulk(es, chunk);
            }
        } catch (Exception e) {
            // 双写模式：ES 失败不影响 MySQL 入库
            log.warn("ES 日志写入失败（已忽略，不影响 MySQL）: {}", e.getMessage());
        }
    }

    private ElasticsearchClient client() {
        return clientProvider.getClient();
    }

    private void bulk(ElasticsearchClient es, List<LogEntry> chunk) throws IOException {
        String index = indexName();
        BulkRequest.Builder builder = new BulkRequest.Builder();
        for (LogEntry entry : chunk) {
            String id = entry.getId() == null ? null : String.valueOf(entry.getId());
            builder.operations(op -> op.index(i -> i.index(index).id(id).document(entry)));
        }
        BulkResponse response = es.bulk(builder.build());
        if (response.errors()) {
            long failed = response.items().stream().filter(item -> item.error() != null).count();
            log.warn("ES bulk 写入索引 {} 共 {} 条，失败 {} 条", index, chunk.size(), failed);
        }
    }

    /** 按天索引名：log-entry-2026-08-07 */
    private String indexName() {
        return properties.getIndexPrefix() + "-" + LocalDate.now();
    }
}
