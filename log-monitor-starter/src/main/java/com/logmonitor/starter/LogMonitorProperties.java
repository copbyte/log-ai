package com.logmonitor.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志监控SDK配置属性
 * <p>
 * 示例：
 * <pre>
 * log-monitor:
 *   mode: http              # http 或 mq，默认 http
 *   url: http://log-service:8081   # HTTP 模式必填
 *   batch-size: 50                  # HTTP 模式批量大小
 *   exchange: log.monitor.log.exchange  # MQ 模式可覆盖
 *   routing-key: log.new                # MQ 模式可覆盖
 * </pre>
 */
@ConfigurationProperties(prefix = "log-monitor")
public class LogMonitorProperties {

    /** 发送模式：http（默认，调 REST API）或 mq（直连 RabbitMQ） */
    private String mode = "http";

    /** HTTP 模式：log-service 地址 */
    private String url = "http://localhost:8081";

    /** HTTP 模式：批量发送大小 */
    private int batchSize = 50;

    /** HTTP/MQ 通用：发送超时（毫秒） */
    private int timeout = 3000;

    /** MQ 模式：Exchange 名称 */
    private String exchange = "log.monitor.log.exchange";

    /** MQ 模式：路由键 */
    private String routingKey = "log.new";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
}
