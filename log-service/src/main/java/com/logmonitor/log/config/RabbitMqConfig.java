package com.logmonitor.log.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    private static final String LOG_EXCHANGE = "log.monitor.log.exchange";
    private static final String DLX_EXCHANGE = "log.monitor.dlx.exchange";
    public static final String AI_QUEUE = "log.monitor.ai.queue";
    public static final String ALERT_QUEUE = "log.monitor.alert.queue";
    public static final String DLX_QUEUE = "log.monitor.dlx.queue";
    public static final String ROUTING_KEY = "log.new";

    @Bean
    public TopicExchange logExchange() {
        return new TopicExchange(LOG_EXCHANGE);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("#");
    }

    @Bean
    public Queue aiQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-message-ttl", 60000);
        return new Queue(AI_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue alertQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-message-ttl", 60000);
        return new Queue(ALERT_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding aiQueueBinding() {
        return BindingBuilder.bind(aiQueue()).to(logExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding alertQueueBinding() {
        return BindingBuilder.bind(alertQueue()).to(logExchange()).with(ROUTING_KEY);
    }
}
