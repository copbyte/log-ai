package com.logmonitor.log.parser;

import com.logmonitor.common.entity.LogEntry;

/**
 * 日志解析器接口：将单行日志文本解析为 LogEntry
 * <p>
 * 不同日志框架（logback/log4j2/JSON 等）输出格式不同，
 * 每种格式对应一个 LogParser 实现。
 */
public interface LogParser {

    /**
     * 解析单行日志
     *
     * @param fileName 日志文件名（用于推断服务名）
     * @param line     单行日志原始文本
     * @return 解析结果，解析失败返回 null
     */
    LogEntry parse(String fileName, String line);

    /**
     * 探测该 Parser 是否能匹配给定样本行
     * <p>
     * Registry 在文件首次读取时取前若干行调用所有 Parser 的 matches，
     * 命中率最高的 Parser 会被绑定到该文件后续解析。
     *
     * @param sampleLine 样本行
     * @return true 表示可匹配
     */
    boolean matches(String sampleLine);

    /**
     * Parser 名称，用于日志和调试
     */
    String name();
}
