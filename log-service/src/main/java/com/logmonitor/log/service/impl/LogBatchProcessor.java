package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志批处理异步处理器（预留的异步扩展点）
 * <p>
 * 背景：v2.0 改造后 WebSocket 广播与 RabbitMQ 分发已移除，当前方法体为空实现。
 * 保留本类及 {@link com.logmonitor.log.config.AsyncConfig} 线程池，作为批次后置处理的
 * 统一异步入口，后续需要以下任一能力时直接在本方法中实现即可复用现有线程池：
 * <ul>
 *   <li>WebSocket 实时日志推送（前端实时流）</li>
 *   <li>RabbitMQ / Kafka 消息分发</li>
 *   <li>批次级 AI 分析、告警触发等后置任务</li>
 * </ul>
 * 线程池参数（核心/最大线程数、队列容量、拒绝策略）见 AsyncConfig#logBatchExecutor。
 */
@Slf4j
@Component
public class LogBatchProcessor {

    /**
     * 异步批量处理日志条目（当前为空实现，预留扩展）
     * <p>
     * 在独立线程池中执行，不阻塞 FileWatchService 主轮询线程的下一次文件扫描。
     *
     * @param entries 已持久化的日志条目列表
     */
    @Async("logBatchExecutor")
    public void processBatchAsync(List<LogEntry> entries) {
        // 预留：WebSocket 推送 / MQ 分发 / AI 分析等批次后置逻辑在此实现
    }
}
