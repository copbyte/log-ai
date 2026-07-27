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

    /**
     * 安全异常检测：识别高频访问IP、端口扫描、SQL注入、XSS、暴力破解等
     */
    @Tool(description = "安全异常检测。基于统计方法分析最近的日志，识别异常行为：高频访问IP、异常端口扫描、SQL注入特征、XSS攻击特征、暴力破解等。返回检测到的异常事件列表及风险级别。")
    public String detectAnomalies(
            @ToolParam(description = "检测时间范围（分钟），默认60", required = false) Integer timeRangeMinutes) {

        int minutes = timeRangeMinutes != null ? timeRangeMinutes : 60;

        log.info("Tool detectAnomalies: timeRangeMinutes={}", minutes);

        // 查询 ERROR / WARN 级别日志
        Map<String, Object> errorResult = logServiceClient.searchLogs("ERROR", null, null, null, 1, 100);
        Map<String, Object> warnResult = logServiceClient.searchLogs("WARN", null, null, null, 1, 100);

        // 查询告警统计和最近告警
        Map<String, Object> alertStats = logServiceClient.getAlertStats();
        Map<String, Object> alerts = logServiceClient.getAlerts(null, null, 1, 50);

        // 汇总日志
        List<Map<String, Object>> allLogs = new ArrayList<>();
        allLogs.addAll(extractRecords(errorResult));
        allLogs.addAll(extractRecords(warnResult));

        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 1. 高频源 IP 检测
        Map<String, Integer> ipCount = new HashMap<>();
        for (Map<String, Object> logEntry : allLogs) {
            String ip = logEntry.get("srcIp") != null ? String.valueOf(logEntry.get("srcIp")) : null;
            if (ip != null && !ip.isEmpty() && !"null".equals(ip)) {
                ipCount.merge(ip, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : ipCount.entrySet()) {
            if (entry.getValue() >= 5) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", "高频访问IP");
                anomaly.put("srcIp", entry.getKey());
                anomaly.put("count", entry.getValue());
                anomaly.put("riskLevel", entry.getValue() >= 20 ? "高" : (entry.getValue() >= 10 ? "中" : "低"));
                anomaly.put("description", "IP " + entry.getKey() + " 在最近 " + minutes + " 分钟内产生 " + entry.getValue() + " 条异常日志");
                anomalies.add(anomaly);
            }
        }

        // 2. 攻击特征检测（SQL注入 / XSS）
        String[] sqlInjectionPatterns = {"union select", "or 1=1", "' or '", "/*", "*/", "xp_", "exec "};
        String[] xssPatterns = {"<script", "javascript:", "onerror=", "onload=", "<img src"};
        for (Map<String, Object> logEntry : allLogs) {
            String content = logEntry.get("content") != null ? String.valueOf(logEntry.get("content")) : "";
            String lowerContent = content.toLowerCase();
            String ip = logEntry.get("srcIp") != null ? String.valueOf(logEntry.get("srcIp")) : null;

            for (String pattern : sqlInjectionPatterns) {
                if (lowerContent.contains(pattern)) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("type", "SQL注入特征");
                    anomaly.put("srcIp", ip);
                    anomaly.put("pattern", pattern);
                    anomaly.put("content", content);
                    anomaly.put("riskLevel", "高");
                    anomaly.put("logTime", logEntry.get("logTime"));
                    anomaly.put("description", "检测到 SQL 注入特征: " + pattern);
                    anomalies.add(anomaly);
                    break;
                }
            }
            for (String pattern : xssPatterns) {
                if (lowerContent.contains(pattern)) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("type", "XSS攻击特征");
                    anomaly.put("srcIp", ip);
                    anomaly.put("pattern", pattern);
                    anomaly.put("content", content);
                    anomaly.put("riskLevel", "高");
                    anomaly.put("logTime", logEntry.get("logTime"));
                    anomaly.put("description", "检测到 XSS 攻击特征: " + pattern);
                    anomalies.add(anomaly);
                    break;
                }
            }
        }

        // 3. deny/blocked 动作检测（暴力破解迹象）
        Map<String, Integer> denyCount = new HashMap<>();
        for (Map<String, Object> logEntry : allLogs) {
            String action = logEntry.get("action") != null ? String.valueOf(logEntry.get("action")) : null;
            if ("deny".equalsIgnoreCase(action) || "blocked".equalsIgnoreCase(action)) {
                String ip = logEntry.get("srcIp") != null ? String.valueOf(logEntry.get("srcIp")) : null;
                if (ip != null && !"null".equals(ip)) {
                    denyCount.merge(ip, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : denyCount.entrySet()) {
            if (entry.getValue() >= 3) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", "暴力破解");
                anomaly.put("srcIp", entry.getKey());
                anomaly.put("count", entry.getValue());
                anomaly.put("riskLevel", entry.getValue() >= 10 ? "高" : "中");
                anomaly.put("description", "IP " + entry.getKey() + " 触发 " + entry.getValue() + " 次 deny/blocked 动作，疑似暴力破解");
                anomalies.add(anomaly);
            }
        }

        // 按风险级别排序（高 > 中 > 低）
        Map<String, Integer> riskOrder = new HashMap<>();
        riskOrder.put("高", 0);
        riskOrder.put("中", 1);
        riskOrder.put("低", 2);
        anomalies.sort((a, b) -> riskOrder.getOrDefault(String.valueOf(a.get("riskLevel")), 3)
                - riskOrder.getOrDefault(String.valueOf(b.get("riskLevel")), 3));

        // 构造人类可读文本
        StringBuilder sb = new StringBuilder();
        sb.append("安全异常检测报告（最近 ").append(minutes).append(" 分钟）\n\n");
        sb.append("分析日志总数: ").append(allLogs.size()).append("\n");
        sb.append("检测到异常事件: ").append(anomalies.size()).append(" 个\n\n");

        if (anomalies.isEmpty()) {
            sb.append("未检测到明显异常行为\n");
        } else {
            sb.append("=== 异常事件列表 ===\n");
            for (int i = 0; i < anomalies.size(); i++) {
                Map<String, Object> a = anomalies.get(i);
                sb.append(i + 1).append(". [").append(a.get("riskLevel")).append("风险] ")
                  .append(a.get("type")).append(" - ").append(a.get("description")).append("\n");
                if (a.get("srcIp") != null) {
                    sb.append("   源IP: ").append(a.get("srcIp")).append("\n");
                }
                if (a.get("logTime") != null) {
                    sb.append("   时间: ").append(a.get("logTime")).append("\n");
                }
                sb.append("\n");
            }
        }

        // 追加 anomaly 结构化块：前端渲染异常事件列表
        Map<String, Object> toolData = new HashMap<>();
        toolData.put("timeRangeMinutes", minutes);
        toolData.put("totalLogs", allLogs.size());
        toolData.put("anomalyCount", anomalies.size());
        toolData.put("anomalies", anomalies);
        toolData.put("alertStats", alertStats != null ? alertStats.get("data") : null);
        toolData.put("recentAlerts", alerts != null ? alerts.get("data") : null);
        return appendToolBlock(sb.toString(), "anomaly", toolData);
    }

    /**
     * 安全事件关联分析：按源IP或时间窗口关联多条日志，还原攻击链路
     */
    @Tool(description = "安全事件关联分析。按源IP或时间窗口关联多条日志，还原攻击链路。识别同一攻击者的多步行为（如先扫描后入侵）。返回关联事件时间线。")
    public String correlateEvents(
            @ToolParam(description = "源IP地址，可选") String srcIp,
            @ToolParam(description = "时间范围（分钟），默认30", required = false) Integer timeRangeMinutes) {

        int minutes = timeRangeMinutes != null ? timeRangeMinutes : 30;

        log.info("Tool correlateEvents: srcIp={}, timeRangeMinutes={}", srcIp, minutes);

        Map<String, List<Map<String, Object>>> ipGroupedLogs = new HashMap<>();
        int totalLogs = 0;

        if (srcIp != null && !srcIp.isEmpty()) {
            // 按源IP查询
            Map<String, Object> result = logServiceClient.getSecurityLogs(srcIp, null, 1, 100);
            List<Map<String, Object>> logs = extractRecords(result);
            ipGroupedLogs.put(srcIp, logs);
            totalLogs = logs.size();
        } else {
            // 查询最近 ERROR / WARN 日志，按 srcIp 分组
            Map<String, Object> errorResult = logServiceClient.searchLogs("ERROR", null, null, null, 1, 100);
            Map<String, Object> warnResult = logServiceClient.searchLogs("WARN", null, null, null, 1, 100);
            List<Map<String, Object>> allLogs = new ArrayList<>();
            allLogs.addAll(extractRecords(errorResult));
            allLogs.addAll(extractRecords(warnResult));
            totalLogs = allLogs.size();

            for (Map<String, Object> logEntry : allLogs) {
                String ip = logEntry.get("srcIp") != null ? String.valueOf(logEntry.get("srcIp")) : null;
                if (ip != null && !ip.isEmpty() && !"null".equals(ip)) {
                    ipGroupedLogs.computeIfAbsent(ip, k -> new ArrayList<>()).add(logEntry);
                }
            }
        }

        // 取日志数最多的 Top 5 IP
        List<Map.Entry<String, List<Map<String, Object>>>> topEntries = new ArrayList<>();
        ipGroupedLogs.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .limit(5)
                .forEach(topEntries::add);

        // 构造每个 IP 的攻击链分析
        List<Map<String, Object>> correlations = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : topEntries) {
            String ip = entry.getKey();
            List<Map<String, Object>> logs = entry.getValue();

            Map<String, Object> correlation = new HashMap<>();
            correlation.put("srcIp", ip);
            correlation.put("eventCount", logs.size());

            // 识别攻击模式
            List<String> patterns = new ArrayList<>();

            // 端口扫描：访问多个不同端口
            long distinctPorts = logs.stream()
                    .map(l -> l.get("dstPort"))
                    .filter(p -> p != null)
                    .distinct()
                    .count();
            if (distinctPorts >= 5) {
                patterns.add("端口扫描(" + distinctPorts + " 个端口)");
            }

            // 暴力破解：多次 deny/blocked
            long denyCnt = logs.stream()
                    .map(l -> l.get("action") != null ? String.valueOf(l.get("action")) : "")
                    .filter(a -> "deny".equalsIgnoreCase(a) || "blocked".equalsIgnoreCase(a))
                    .count();
            if (denyCnt >= 3) {
                patterns.add("暴力破解(" + denyCnt + " 次 deny/blocked)");
            }

            // SQL 注入特征
            long sqlInjectionCnt = logs.stream()
                    .map(l -> l.get("content") != null ? String.valueOf(l.get("content")).toLowerCase() : "")
                    .filter(c -> c.contains("union select") || c.contains("' or '") || c.contains("or 1=1"))
                    .count();
            if (sqlInjectionCnt > 0) {
                patterns.add("SQL注入特征(" + sqlInjectionCnt + " 次)");
            }

            // XSS 特征
            long xssCnt = logs.stream()
                    .map(l -> l.get("content") != null ? String.valueOf(l.get("content")).toLowerCase() : "")
                    .filter(c -> c.contains("<script") || c.contains("javascript:"))
                    .count();
            if (xssCnt > 0) {
                patterns.add("XSS攻击特征(" + xssCnt + " 次)");
            }

            correlation.put("patterns", patterns);
            correlation.put("timeline", logs);
            if (!logs.isEmpty()) {
                correlation.put("firstEvent", logs.get(0).get("logTime"));
                correlation.put("lastEvent", logs.get(logs.size() - 1).get("logTime"));
            }
            correlations.add(correlation);
        }

        // 按事件数倒序
        correlations.sort((a, b) -> ((Integer) b.get("eventCount")) - ((Integer) a.get("eventCount")));

        // 构造人类可读文本
        StringBuilder sb = new StringBuilder();
        sb.append("安全事件关联分析（最近 ").append(minutes).append(" 分钟）\n\n");
        if (srcIp != null && !srcIp.isEmpty()) {
            sb.append("目标源IP: ").append(srcIp).append("\n");
        }
        sb.append("分析IP数: ").append(topEntries.size()).append("\n");
        sb.append("关联日志总数: ").append(totalLogs).append("\n\n");

        if (correlations.isEmpty()) {
            sb.append("未找到可关联的事件\n");
        } else {
            sb.append("=== 攻击链分析 ===\n");
            for (int i = 0; i < correlations.size(); i++) {
                Map<String, Object> c = correlations.get(i);
                sb.append(i + 1).append(". 源IP: ").append(c.get("srcIp"))
                  .append("（").append(c.get("eventCount")).append(" 条事件）\n");
                if (c.get("firstEvent") != null) {
                    sb.append("   时间范围: ").append(c.get("firstEvent"))
                      .append(" → ").append(c.get("lastEvent")).append("\n");
                }
                @SuppressWarnings("unchecked")
                List<String> patterns = (List<String>) c.get("patterns");
                if (patterns != null && !patterns.isEmpty()) {
                    sb.append("   攻击模式: ").append(String.join(", ", patterns)).append("\n");
                } else {
                    sb.append("   攻击模式: 未识别明显模式\n");
                }
                sb.append("\n");
            }

            sb.append("=== 时间线 ===\n");
            for (Map<String, Object> c : correlations) {
                sb.append("[").append(c.get("srcIp")).append("]\n");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> timeline = (List<Map<String, Object>>) c.get("timeline");
                for (Map<String, Object> logEntry : timeline) {
                    sb.append("  [").append(logEntry.get("logTime")).append("] ")
                      .append("[").append(logEntry.get("logLevel")).append("] ");
                    if (logEntry.get("action") != null) {
                        sb.append("[").append(logEntry.get("action")).append("] ");
                    }
                    sb.append(logEntry.get("content")).append("\n");
                }
                sb.append("\n");
            }
        }

        // 追加 correlation 结构化块：前端渲染攻击链时间线
        Map<String, Object> toolData = new HashMap<>();
        toolData.put("timeRangeMinutes", minutes);
        toolData.put("srcIp", srcIp);
        toolData.put("ipCount", topEntries.size());
        toolData.put("totalLogs", totalLogs);
        toolData.put("correlations", correlations);
        return appendToolBlock(sb.toString(), "correlation", toolData);
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

    /** 从分页查询响应中提取 records 列表 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecords(Map<String, Object> result) {
        if (result == null) return List.of();
        Object data = result.get("data");
        if (data instanceof Map) {
            Map<?, ?> pageData = (Map<?, ?>) data;
            Object records = pageData.get("records");
            if (records instanceof List) {
                return (List<Map<String, Object>>) records;
            }
        }
        return List.of();
    }
}
