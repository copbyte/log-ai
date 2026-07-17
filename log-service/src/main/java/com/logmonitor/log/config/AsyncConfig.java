package com.logmonitor.log.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步处理线程池配置
 * <p>
 * 为日志批处理的异步任务提供独立线程池，
 * 避免异步逻辑阻塞主轮询线程的文件读取和数据库批量写入。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 日志批处理专用异步线程池
     * <p>
     * 核心线程数保持少量常驻，最大线程数限制峰值扩容，
     * 拒绝策略使用CallerRunsPolicy确保任务不丢失（过载时降级为同步执行）。
     */
    @Bean("logBatchExecutor")
    public Executor logBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：保持少量常驻线程处理日常异步任务
        executor.setCorePoolSize(4);
        // 最大线程数：高峰期可扩展的上限
        executor.setMaxPoolSize(8);
        // 有界队列：缓冲待处理任务，避免无限制堆积
        executor.setQueueCapacity(200);
        // 线程名前缀：便于日志排查
        executor.setThreadNamePrefix("log-batch-");
        // 拒绝策略：由调用线程执行，避免任务丢失（当日志写入负载过高时降级为同步执行）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
