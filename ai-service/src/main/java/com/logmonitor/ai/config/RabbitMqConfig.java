package com.logmonitor.ai.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    public static final String AI_QUEUE = "log.monitor.ai.queue";
    private static final String LOG_EXCHANGE = "log.monitor.log.exchange";
    private static final String DLX_EXCHANGE = "log.monitor.dlx.exchange";

    /**
     * 声明日志 Exchange（启动顺序无关——即使 log-service 未先启动，Exchange 也存在）
     */
    @Bean
    public TopicExchange logMonitorExchange() {
        return new TopicExchange(LOG_EXCHANGE);
    }

    /**
     * AI分析队列：持久化，绑定死信交换机
     * x-message-ttl: 消息10分钟后过期
     * x-max-length: 队列最多积压5万条
     */
    @Bean
    public Queue aiQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-message-ttl", 600_000);
        args.put("x-max-length", 50_000);
        return new Queue(AI_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding aiQueueBinding() {
        return BindingBuilder.bind(aiQueue()).to(logMonitorExchange()).with("log.new");
    }
}
