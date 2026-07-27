package com.logmonitor.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Syslog 采集配置
 * <p>
 * 通过 log.syslog.enabled 控制是否启用（默认关闭）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "log.syslog")
public class SyslogConfig {

    /** 是否启用 Syslog 采集 */
    private boolean enabled = false;

    /** UDP 监听端口（默认 5140，避免 514 需要 root 权限） */
    private int port = 5140;

    /** 缓冲队列大小 */
    private int bufferSize = 5000;
}
