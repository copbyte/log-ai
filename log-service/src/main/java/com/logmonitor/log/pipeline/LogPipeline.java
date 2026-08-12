package com.logmonitor.log.pipeline;

import com.logmonitor.common.entity.LogEntry;

import java.util.List;

/**
 * 日志采集管道统一入口
 * <p>
 * 采集端（文件监听 / Syslog / 模拟器）只依赖本接口落日志，不关心具体实现：
 * <ul>
 *   <li>direct：直接批量入库（本地演示、无 Kafka 环境降级）</li>
 *   <li>kafka：发送到 Kafka 削峰，由消费端批量入库（生产模式）</li>
 * </ul>
 */
public interface LogPipeline {

    /**
     * 持久化一批日志
     *
     * @param batch 已解析的日志条目
     * @throws RuntimeException 持久化失败时抛出，由调用方决定重试策略（如文件偏移量不推进）
     */
    void persist(List<LogEntry> batch);
}
