package com.logmonitor.alert.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

 //    //TODO 如果第一次弹出启动此服务 需将注释放开一次，已经启动过log-service服务请忽略
//    public static final String LOG_MONITOR_QUEUE = "log.monitor.alert.queue";
//
//    @Bean
//    public Queue logMonitorAlertQueue() {
//        return new Queue(LOG_MONITOR_QUEUE, true);
//    }
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }
}
