package com.logmonitor.mcp.tool;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.mcp.client.LogServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志分析工具集
 * 通过 @Tool 注解注册，同时供 ChatClient（Tool Calling）和 MCP Server 使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogAnalysisTools {

    private final LogServiceClient logServiceClient;

    /**
     * 搜索日志：支持按级别、关键字、服务名、traceId 筛选
     */
    @Tool(description = "搜索日志记录。支持按日志级别(ERROR/WARN/INFO等)、关键字、服务名、TraceID进行筛选。返回匹配的日志列表。")
    public String searchLogs(
            @ToolParam(description = "日志级别，如 ERROR/WARN/INFO/DEBUG，可选") String logLevel,
            @ToolParam(description = "搜索关键字，匹配日志内容、类名、文件名，可选") String keyword,
            @ToolParam(description = "服务名称，如 order-service，可选") String serviceName,
            @ToolParam(description = "页码，默认1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认20", required = false) Integer size) {

        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 20;

        log.info("Tool searchLogs: level={}, keyword={}, service={}, page={}, size={}",
                logLevel, keyword, serviceName, pageNum, pageSize);

        Map<String, Object> result = logServiceClient.searchLogs(logLevel, keyword, null, serviceName, pageNum, pageSize);
        return formatSearchResult(result);
    }

    /**
     * 按 TraceID 查询链路日志：获取同一请求链路的所有日志
     */
    @Tool(description = "按 TraceID 查询链路日志。返回同一请求链路中所有服务的日志记录，按时间升序排列。用于链路追踪和根因分析。")
    public String getTraceLogs(
            @ToolParam(description = "链路追踪 TraceID") String traceId) {

        log.info("Tool getTraceLogs: traceId={}", traceId);

        List<Map<String, Object>> logs = logServiceClient.getLogsByTraceId(traceId);
        return formatTraceLogs(traceId, logs);
    }

    /**
     * 异常聚类：按异常类型和堆栈首行分组，统计每种异常的出现次数
     */
    @Tool(description = "异常聚类分析。按异常类型和堆栈首行分组统计，返回每种异常的出现次数、最近发生时间和示例日志。用于快速识别高频异常。")
    public String clusterExceptions(
            @ToolParam(description = "日志级别过滤，默认ERROR", required = false) String logLevel,
            @ToolParam(description = "每页条数，默认50", required = false) Integer size) {

        String level = logLevel != null ? logLevel : "ERROR";
        int pageSize = size != null ? size : 100;

        log.info("Tool clusterExceptions: level={}, size={}", level, pageSize);

        // 查询 ERROR 级别日志
        Map<String, Object> result = logServiceClient.searchLogs(level, null, null, null, 1, pageSize);
        return formatClusterResult(result);
    }

    /**
     * 根因定位：根据 traceId 获取链路日志，分析异常根因
     */
    @Tool(description = "根因定位分析。根据 TraceID 获取完整链路日志，识别异常传播路径，定位根本原因。返回链路中所有异常日志及其调用关系。")
    public String locateRootCause(
            @ToolParam(description = "链路追踪 TraceID") String traceId) {

        log.info("Tool locateRootCause: traceId={}", traceId);

        List<Map<String, Object>> logs = logServiceClient.getLogsByTraceId(traceId);
        return formatRootCauseAnalysis(traceId, logs);
    }

    // ========== 格式化方法 ==========

    /**
     * 在人类可读文本末尾追加 ```tool:kind\n{json}\n``` 结构化块
     * 前端 MessageItem 解析该块并渲染为可视化组件（TraceTimeline/ClusterChart）
     */
    private String appendToolBlock(String humanText, String kind, Object data) {
        return humanText + "\n\n```tool:" + kind + "\n" + JSON.toJSONString(data) + "\n```\n";
    }

    private String formatSearchResult(Map<String, Object> result) {
        Object data = result.get("data");
        if (data instanceof Map) {
            Map<?, ?> pageData = (Map<?, ?>) data;
            List<?> records = (List<?>) pageData.get("records");
            long total = pageData.get("total") != null ? ((Number) pageData.get("total")).longValue() : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("共找到 ").append(total).append(" 条日志\n\n");
            if (records != null) {
                for (Object record : records) {
                    if (record instanceof Map) {
                        Map<?, ?> log = (Map<?, ?>) record;
                        sb.append("[").append(log.get("logTime")).append("] ")
                          .append("[").append(log.get("logLevel")).append("] ")
                          .append(log.get("serviceName")).append(" - ")
                          .append(log.get("content")).append("\n");
                        if (log.get("traceId") != null) {
                            sb.append("  traceId: ").append(log.get("traceId")).append("\n");
                        }
                    }
                }
            }
            // 追加结构化块：前端搜索结果暂用 Markdown 表格渲染，预留 search 块用于未来增强
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("total", total);
            toolData.put("logs", records != null ? records : List.of());
            return appendToolBlock(sb.toString(), "search", toolData);
        }
        return "未找到日志记录";
    }

    private String formatTraceLogs(String traceId, List<Map<String, Object>> logs) {
        if (logs.isEmpty()) {
            return "未找到 TraceID=" + traceId + " 的链路日志";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TraceID: ").append(traceId).append("，共 ").append(logs.size()).append(" 条链路日志\n\n");
        for (Map<String, Object> log : logs) {
            sb.append("[").append(log.get("logTime")).append("] ")
              .append("[").append(log.get("logLevel")).append("] ")
              .append(log.get("serviceName")).append(" ")
              .append(log.get("className")).append(" - ")
              .append(log.get("content")).append("\n");
            if (log.get("stackTrace") != null) {
                sb.append("  堆栈:\n").append(indent(log.get("stackTrace").toString())).append("\n");
            }
        }
        // 追加 trace 结构化块：前端 TraceTimeline 渲染时间线
        Map<String, Object> toolData = new HashMap<>();
        toolData.put("traceId", traceId);
        toolData.put("logs", logs);
        return appendToolBlock(sb.toString(), "trace", toolData);
    }

    @SuppressWarnings("unchecked")
    private String formatClusterResult(Map<String, Object> result) {
        Object data = result.get("data");
        if (data instanceof Map) {
            Map<?, ?> pageData = (Map<?, ?>) data;
            List<?> records = (List<?>) pageData.get("records");
            if (records == null || records.isEmpty()) {
                return "未找到异常日志";
            }

            // 按异常类型 + 堆栈首行分组
            Map<String, List<Map<String, Object>>> clusters = new HashMap<>();
            for (Object record : records) {
                if (record instanceof Map) {
                    Map<String, Object> log = (Map<String, Object>) record;
                    String clusterKey = buildClusterKey(log);
                    clusters.computeIfAbsent(clusterKey, k -> new java.util.ArrayList<>()).add(log);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("异常聚类结果（共 ").append(clusters.size()).append(" 类异常，").append(records.size()).append(" 条日志）:\n\n");
            // 同时构造结构化 clusters 列表，按出现次数倒序
            List<Map<String, Object>> clusterList = new ArrayList<>();
            clusters.entrySet().stream()
                    .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                    .forEach(entry -> {
                        sb.append("【").append(entry.getValue().size()).append("次】")
                          .append(entry.getKey()).append("\n");
                        Map<String, Object> sample = entry.getValue().get(0);
                        sb.append("  示例: ").append(sample.get("content")).append("\n");
                        if (sample.get("serviceName") != null) {
                            sb.append("  服务: ").append(sample.get("serviceName")).append("\n");
                        }
                        sb.append("\n");

                        // 追加到结构化列表
                        Map<String, Object> item = new HashMap<>();
                        item.put("key", entry.getKey());
                        item.put("count", entry.getValue().size());
                        item.put("sample", sample.get("content"));
                        item.put("serviceName", sample.get("serviceName"));
                        clusterList.add(item);
                    });
            // 追加 cluster 结构化块：前端 ClusterChart 渲染柱状图
            Map<String, Object> toolData = new HashMap<>();
            toolData.put("clusters", clusterList);
            return appendToolBlock(sb.toString(), "cluster", toolData);
        }
        return "异常聚类分析失败";
    }

    private String formatRootCauseAnalysis(String traceId, List<Map<String, Object>> logs) {
        if (logs.isEmpty()) {
            return "未找到 TraceID=" + traceId + " 的链路日志，无法进行根因分析";
        }

        // 识别 ERROR 级别日志
        List<Map<String, Object>> errors = logs.stream()
                .filter(log -> "ERROR".equalsIgnoreCase(String.valueOf(log.get("logLevel"))))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("根因分析报告 - TraceID: ").append(traceId).append("\n\n");
        sb.append("链路日志总数: ").append(logs.size()).append("\n");
        sb.append("异常日志数: ").append(errors.size()).append("\n\n");

        if (!errors.isEmpty()) {
            sb.append("=== 异常日志 ===\n");
            for (Map<String, Object> error : errors) {
                sb.append("[").append(error.get("logTime")).append("] ")
                  .append(error.get("serviceName")).append(" - ")
                  .append(error.get("content")).append("\n");
                if (error.get("stackTrace") != null) {
                    sb.append("堆栈:\n").append(indent(error.get("stackTrace").toString())).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("=== 完整链路时间线 ===\n");
        for (Map<String, Object> log : logs) {
            sb.append("[").append(log.get("logTime")).append("] ")
              .append("[").append(log.get("logLevel")).append("] ")
              .append(log.get("serviceName")).append(" - ")
              .append(log.get("content")).append("\n");
        }

        // 追加 rootCause 结构化块：前端 TraceTimeline 渲染（与 trace 共用组件）
        Map<String, Object> toolData = new HashMap<>();
        toolData.put("traceId", traceId);
        toolData.put("logs", logs);
        toolData.put("errors", errors);
        return appendToolBlock(sb.toString(), "rootCause", toolData);
    }

    /** 构建聚类 key：异常类名 + 方法名（忽略行号） */
    private String buildClusterKey(Map<String, Object> log) {
        String stackTrace = (String) log.get("stackTrace");
        if (stackTrace != null && !stackTrace.isBlank()) {
            String firstLine = stackTrace.split("\n")[0];
            // 提取第二行 at xxx.method(File.java:xxx) 去掉行号
            String[] lines = stackTrace.split("\n");
            if (lines.length > 1) {
                String atLine = lines[1].trim();
                atLine = atLine.replaceAll("\\(\\w+\\.java:\\d+\\)", "(.java)");
                return firstLine + " | " + atLine;
            }
            return firstLine;
        }
        return String.valueOf(log.get("content"));
    }

    private String indent(String text) {
        return java.util.Arrays.stream(text.split("\n"))
                .map(line -> "  " + line)
                .collect(Collectors.joining("\n"));
    }
}
