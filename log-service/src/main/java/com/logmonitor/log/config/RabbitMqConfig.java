package com.logmonitor.log.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置：Exchange及DLQ声明（AI/告警队列已迁移至各自服务自行声明）
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    private static final String LOG_EXCHANGE = "log.monitor.log.exchange";
    private static final String DLX_EXCHANGE = "log.monitor.dlx.exchange";
    public static final String DLX_QUEUE = "log.monitor.dlx.queue";
    public static final String ROUTING_KEY = "log.new";

    /* ========== Exchange定义 ========== */

    @Bean
    public TopicExchange logExchange() {
        return new TopicExchange(LOG_EXCHANGE);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    /* ========== 死信队列 ========== */

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE, true);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("#");
    }

    /* ========== RabbitTemplate可靠发送配置 ========== */

    @Bean



    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到Broker失败: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            }
        });

        template.setReturnsCallback(returned -> {
            log.error("消息无法路由到队列: exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    new String(returned.getMessage().getBody()));
        });

        return template;
    }
}
