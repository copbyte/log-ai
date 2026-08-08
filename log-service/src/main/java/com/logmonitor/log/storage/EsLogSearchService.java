package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ES 日志查询服务：全文检索/多维筛选走 ES，命中数据映射回 LogEntry。
 * <p>
 * 生产模式：日志查询优先 ES（全文检索 + 时间范围聚合远快于 MySQL LIKE），
 * ES 关闭或异常时返回 null，由调用方回退 MySQL，保证查询链路可用。
 */
@Slf4j
@Component
public class EsLogSearchService {

    private static final String INDEX_PATTERN = "log-entry-*";
    private static final int MAX_TRACE_SIZE = 500;

    private final EsProperties properties;
    private final EsClientProvider clientProvider;
    private final ObjectMapper objectMapper;

    public EsLogSearchService(EsProperties properties, EsClientProvider clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 分页搜索（与 MySQL pageWithFilters 同语义：多条件 AND）。
     *
     * @return ES 分页结果；ES 关闭或查询异常返回 null（调用方回退 MySQL）
     */
    public Page<LogEntry> search(Page<LogEntry> page,
                                 String logLevel,
                                 String className,
                                 String fileName,
                                 String threadName,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime,
                                 String keyword,
                                 String traceId,
                                 String serviceName,
                                 String logSource,
                                 String srcIp,
                                 String action) {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();
            // 关键字全文检索：内容/类名/文件名
            if (StringUtils.hasText(keyword)) {
                bool.must(m -> m.multiMatch(mm -> mm
                        .query(keyword)
                        .fields("content", "className", "fileName")));
            }
            // 精确字段过滤（与 MySQL eq 对应）
            // 精确字段统一查 keyword 子字段：text 会被分词（如 ERROR -> error），term 直接查 text 会漏匹配
            termFilter(bool, "logLevel.keyword", logLevel);
            termFilter(bool, "traceId.keyword", traceId);
            termFilter(bool, "serviceName.keyword", serviceName);
            termFilter(bool, "logSource.keyword", logSource);
            termFilter(bool, "srcIp.keyword", srcIp);
            termFilter(bool, "action.keyword", action);
            // 模糊字段过滤（与 MySQL like 对应）：keyword 子字段通配
            wildcardFilter(bool, "className.keyword", className);
            wildcardFilter(bool, "fileName.keyword", fileName);
            wildcardFilter(bool, "threadName.keyword", threadName);
            // 时间范围
            if (startTime != null || endTime != null) {
                bool.filter(f -> f.range(r -> {
                    r.field("logTime");
                    if (startTime != null) {
                        r.gte(JsonData.of(startTime.toString()));
                    }
                    if (endTime != null) {
                        r.lte(JsonData.of(endTime.toString()));
                    }
                    return r;
                }));
            }

            int from = (int) Math.max(0, (page.getCurrent() - 1) * page.getSize());
            int size = (int) Math.min(page.getSize(), 1000);
            SearchRequest request = new SearchRequest.Builder()
                    .index(INDEX_PATTERN)
                    .query(q -> q.bool(bool.build()))
                    .sort(s -> s.field(f -> f.field("logTime").order(SortOrder.Desc)))
                    .sort(s -> s.field(f -> f.field("id").order(SortOrder.Desc)))
                    .from(from)
                    .size(size)
                    // 精确 total，保证前端分页条数与 MySQL 一致
                    .trackTotalHits(t -> t.enabled(true))
                    .build();

            SearchResponse<JsonData> response =
                    clientProvider.getClient().search(request, JsonData.class);
            List<LogEntry> records = new ArrayList<>();
            if (response.hits() != null && response.hits().hits() != null) {
                for (Hit<JsonData> hit : response.hits().hits()) {
                    if (hit.source() != null) {
                        records.add(toLogEntry(hit.source()));
                    }
                }
            }
            long total = response.hits() != null && response.hits().total() != null
                    ? response.hits().total().value()
                    : records.size();
            Page<LogEntry> result = new Page<>(page.getCurrent(), page.getSize(), total);
            result.setRecords(records);
            return result;
        } catch (Exception e) {
            log.warn("ES 日志查询失败，将回退 MySQL: {}", e.getMessage(), e);
            return null;
        }
    }

    /** 按 TraceID 查询链路日志（时间升序）；异常返回 null 由调用方回退 MySQL */
    public List<LogEntry> findByTraceId(String traceId) {
        if (!properties.isEnabled() || !StringUtils.hasText(traceId)) {
            return null;
        }
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();
            bool.filter(f -> f.term(t -> t.field("traceId.keyword").value(traceId)));
            SearchRequest request = new SearchRequest.Builder()
                    .index(INDEX_PATTERN)
                    .query(q -> q.bool(bool.build()))
                    .sort(s -> s.field(f -> f.field("logTime").order(SortOrder.Asc)))
                    .size(MAX_TRACE_SIZE)
                    .build();
            SearchResponse<JsonData> response =
                    clientProvider.getClient().search(request, JsonData.class);
            List<LogEntry> logs = new ArrayList<>();
            if (response.hits() != null && response.hits().hits() != null) {
                for (Hit<JsonData> hit : response.hits().hits()) {
                    if (hit.source() != null) {
                        logs.add(toLogEntry(hit.source()));
                    }
                }
            }
            return logs;
        } catch (Exception e) {
            log.warn("ES TraceID 查询失败，将回退 MySQL: {}", e.getMessage(), e);
            return null;
        }
    }

    private void termFilter(BoolQuery.Builder bool, String field, String value) {
        if (StringUtils.hasText(value)) {
            bool.filter(f -> f.term(t -> t.field(field).value(value)));
        }
    }

    private void wildcardFilter(BoolQuery.Builder bool, String field, String value) {
        if (StringUtils.hasText(value)) {
            bool.filter(f -> f.wildcard(w -> w.field(field).value("*" + value + "*")));
        }
    }

    private LogEntry toLogEntry(JsonData source) throws Exception {
        return objectMapper.readValue(source.toString(), LogEntry.class);
    }
}
