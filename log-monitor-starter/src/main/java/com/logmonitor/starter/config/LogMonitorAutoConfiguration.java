package com.logmonitor.starter.config;

import com.logmonitor.starter.HttpLogMonitorClient;
import com.logmonitor.starter.LogMonitorClient;
import com.logmonitor.starter.LogMonitorProperties;
import com.logmonitor.starter.MqLogMonitorClient;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 日志监控SDK自动装配 — 按 mode 配置选择 HTTP 或 MQ 实现
 * <p>
 * 默认模式 {@code http}：通过 RestTemplate POST 到 log-service
 * <p>
 * MQ 模式 {@code mq}：直连 RabbitMQ Exchange（需配置 spring.rabbitmq.*）
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "log-monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogMonitorProperties.class)
public class LogMonitorAutoConfiguration {

    private final LogMonitorProperties properties;

    public LogMonitorAutoConfiguration(LogMonitorProperties properties) {
        this.properties = properties;
    }

    /* ========== HTTP 模式 ========== */

    @Bean
    @ConditionalOnProperty(prefix = "log-monitor", name = "mode", havingValue = "http", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "log-monitor", name = "mode", havingValue = "http", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogMonitorClient httpLogMonitorClient(RestTemplate restTemplate) {
        return new HttpLogMonitorClient(restTemplate, properties);
    }

    /* ========== MQ 模式 ========== */

    @Bean
    @ConditionalOnProperty(prefix = "log-monitor", name = "mode", havingValue = "mq")
    @ConditionalOnClass(RabbitTemplate.class)
    public TopicExchange logMonitorExchange() {
        return new TopicExchange(properties.getExchange());
    }

    @Bean
    @ConditionalOnProperty(prefix = "log-monitor", name = "mode", havingValue = "mq")
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public LogMonitorClient mqLogMonitorClient(RabbitTemplate rabbitTemplate) {
        return new MqLogMonitorClient(rabbitTemplate, properties);
    }
}
