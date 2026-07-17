package com.logmonitor.common.enums;

/**
 * 日志来源类型
 */
public enum LogSource {
    /** 文件监控采集 */
    FILE,
    /** API 主动上报 */
    HTTP,
    /** Elasticsearch (ELK) 适配 */
    ELK,
    /** Grafana Loki 适配 */
    LOKI,
    /** 内置 Mock 测试数据 */
    MOCK
}
