package com.logmonitor.ai.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

//    TODO 第一次单独启动该服务时必需要将以下注释放开一次，创建队列
//    // 队列名称，必须和你 @RabbitListener 里监听的名字完全一致
//    public static final String LOG_MONITOR_QUEUE = "log.monitor.ai.queue";
//
//    /**
//     * 声明队列：Spring启动时会自动在RabbitMQ中创建这个队列
//     * 参数1：队列名称
//     * 参数2：durable=true 持久化（RabbitMQ重启后队列不丢失）
//     * 参数3：exclusive=false 非排他（允许多个连接访问）
//     * 参数4：autoDelete=false 不自动删除
//     */
//    @Bean
//    public Queue logMonitorQueue() {
//        return new Queue(LOG_MONITOR_QUEUE, true, false, false);
//    }


    //   ACK监听容器工厂，用于处理手动确认模式
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
