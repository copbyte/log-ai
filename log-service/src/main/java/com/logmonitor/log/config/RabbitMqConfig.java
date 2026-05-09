package com.logmonitor.log.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ配置：Exchange、Queue、Binding声明及RabbitTemplate回调
 * <p>
 * 所有队列声明为持久化（durable=true），服务重启后队列不丢失。
 * Exchange和Queue在应用启动时自动声明，无需手动在RabbitMQ管理界面创建。
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    private static final String LOG_EXCHANGE = "log.monitor.log.exchange";
    private static final String DLX_EXCHANGE = "log.monitor.dlx.exchange";
    public static final String AI_QUEUE = "log.monitor.ai.queue";
    public static final String ALERT_QUEUE = "log.monitor.alert.queue";
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

    /* ========== 工作队列定义 ========== */

    /**
     * AI分析队列：持久化，绑定死信交换机
     * 移除x-message-ttl避免消息在等待AI处理期间（可能超过60秒）被丢弃
     */
    @Bean
    public Queue aiQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(AI_QUEUE, true, false, false, args);
    }

    /**
     * 告警队列：持久化，绑定死信交换机
     */
    @Bean
    public Queue alertQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(ALERT_QUEUE, true, false, false, args);
    }

    /**
     * 死信队列：持久化，接收消费失败的消息
     */
    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE, true);
    }

    /* ========== Binding定义 ========== */

    @Bean
    public Binding aiQueueBinding() {
        return BindingBuilder.bind(aiQueue()).to(logExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding alertQueueBinding() {
        return BindingBuilder.bind(alertQueue()).to(logExchange()).with(ROUTING_KEY);
    }

    /**
     * 死信队列绑定：接收所有来自死信交换机的消息（#通配符匹配所有路由键）
     */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("#");
    }

    /* ========== RabbitTemplate可靠发送配置 ========== */

    /**
     * 配置RabbitTemplate的消息确认和返回回调
     * <p>
     * ConfirmCallback：消息到达Broker（Exchange）时回调，确认消息未丢失在传输层。
     * ReturnCallback：消息到达Exchange但无法路由到任何Queue时回调（需配合mandatory=true）。
     * 两者配合实现端到端的消息可靠性保障，杜绝静默丢消息。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        // 消息到达Broker回调：ack=true表示已确认，ack=false和cause表示失败原因
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("消息已确认到达Broker: correlationId={}",
                        correlationData != null ? correlationData.getId() : null);
            } else {
                log.error("消息发送到Broker失败: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            }
        });

        // 消息无法路由到队列回调：由于已开启mandatory=true，无法路由的消息会触发此回调而非静默丢弃
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
