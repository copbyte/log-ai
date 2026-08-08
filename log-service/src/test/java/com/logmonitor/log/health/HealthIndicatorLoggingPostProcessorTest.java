package com.logmonitor.log.health;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 健康状态变化日志测试：状态跳变才记 WARN，状态不变不重复打日志。
 */
class HealthIndicatorLoggingPostProcessorTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(
                HealthIndicatorLoggingPostProcessor.LoggingHealthIndicator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void logsOnlyOnStatusChange() {
        HealthIndicator delegate = mock(HealthIndicator.class);
        when(delegate.health())
                .thenReturn(Health.up().build())
                .thenReturn(Health.up().build())
                .thenReturn(Health.down().build())
                .thenReturn(Health.up().build());
        HealthIndicatorLoggingPostProcessor.LoggingHealthIndicator wrapper =
                new HealthIndicatorLoggingPostProcessor.LoggingHealthIndicator("dbHealthIndicator", delegate);

        assertEquals(Status.UP, wrapper.health().getStatus());
        assertEquals(Status.UP, wrapper.health().getStatus());
        assertEquals(Status.DOWN, wrapper.health().getStatus());
        assertEquals(Status.UP, wrapper.health().getStatus());

        List<ILoggingEvent> warnEvents = appender.list.stream()
                .filter(e -> e.getLevel().toString().equals("WARN"))
                .toList();
        assertEquals(2, warnEvents.size());
        assertTrue(warnEvents.get(0).getFormattedMessage().contains("UP -> DOWN"));
        assertTrue(warnEvents.get(1).getFormattedMessage().contains("DOWN -> UP"));
    }
}
