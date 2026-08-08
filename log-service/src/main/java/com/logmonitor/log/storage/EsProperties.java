package com.logmonitor.log.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ES 双写配置（log.storage.es.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "log.storage.es")
public class EsProperties {

    /** 开关：true 时日志入库 MySQL 的同时批量写入 ES（ES 故障不影响 MySQL） */
    private boolean enabled = false;

    /** ES 地址，如 http://82.156.4.200:9200 */
    private String uris = "http://localhost:9200";

    /** 索引前缀，实际索引按天：{prefix}-2026-08-07 */
    private String indexPrefix = "log-entry";

    /** 单次 bulk 写入条数 */
    private int batchSize = 500;
}
