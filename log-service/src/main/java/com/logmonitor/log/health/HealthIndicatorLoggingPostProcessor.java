package com.logmonitor.log.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * 健康检查状态变化日志：包装所有 HealthIndicator，仅在状态发生跳变时打日志。
 * <p>
 * 默认健康检查每次探测都静默（返回 JSON 即可），但生产排查时想知道“什么时候开始挂、什么时候恢复”，
 * 因此这里记录：初始状态（DEBUG）、状态变化（WARN，如 UP -> DOWN / DOWN -> UP），
 * 状态不变时零日志，避免 K8s 每 5 秒探测刷屏。
 */
@Component
public class HealthIndicatorLoggingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HealthIndicator indicator && !(bean instanceof LoggingHealthIndicator)) {
            return new LoggingHealthIndicator(beanName, indicator);
        }
        return bean;
    }

    /** 日志包装器：委托原指示器并记录状态跳变 */
    static class LoggingHealthIndicator implements HealthIndicator {

        private static final Logger log =
                LoggerFactory.getLogger(LoggingHealthIndicator.class);

        private final String name;
        private final HealthIndicator delegate;
        private volatile Status lastStatus;

        LoggingHealthIndicator(String beanName, HealthIndicator delegate) {
            // dbHealthIndicator -> db，日志更可读
            this.name = beanName.replace("HealthIndicator", "");
            this.delegate = delegate;
        }

        @Override
        public Health health() {
            Health health = delegate.health();
            Status status = health.getStatus();
            Status previous = lastStatus;
            lastStatus = status;
            if (previous == null) {
                log.debug("健康检查 [{}] 初始状态: {}", name, status);
            } else if (!previous.equals(status)) {
                log.warn("健康检查 [{}] 状态变化: {} -> {}", name, previous, status);
            }
            return health;
        }
    }
}
