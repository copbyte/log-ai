package com.logmonitor.log.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志解析器注册中心
 * <p>
 * 维护所有可用的 LogParser 实现，并为每个日志文件绑定一个最合适的 Parser。
 * 绑定策略：取文件前若干行样本，统计各 Parser 命中次数，选命中率最高者；
 * 若所有 Parser 都不命中，使用 PlainLogParser 兜底。
 */
@Slf4j
@Component
public class LogParserRegistry {

    /** 所有 Parser（顺序即优先级，PlainLogParser 必须在最后） */
    private final List<LogParser> parsers;

    /** 兜底 Parser */
    private final LogParser fallback = new PlainLogParser();

    /** 文件路径 -> 绑定的 Parser */
    private final Map<String, LogParser> boundParsers = new ConcurrentHashMap<>();

    public LogParserRegistry() {
        this.parsers = new ArrayList<>();
        // 顺序即优先级：JSON 优先（最容易区分），然后按常见度排列
        this.parsers.add(new JsonLogParser());
        this.parsers.add(new LogbackCustomParser());
        this.parsers.add(new LogbackDefaultParser());
        this.parsers.add(new Log4j2DefaultParser());
    }

    /**
     * 为指定文件绑定 Parser（基于样本行探测）
     */
    public LogParser bind(String filePath, List<String> sampleLines) {
        LogParser best = null;
        int bestHits = 0;

        for (LogParser parser : parsers) {
            int hits = 0;
            for (String line : sampleLines) {
                if (line != null && !line.isBlank() && parser.matches(line)) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = parser;
            }
        }

        LogParser bound = best != null ? best : fallback;
        boundParsers.put(filePath, bound);
        log.info("Parser bound: file={}, parser={}, hits={}/{}",
                filePath, bound.name(), bestHits, sampleLines.size());
        return bound;
    }

    /**
     * 获取文件绑定的 Parser；未绑定时返回兜底 Parser
     */
    public LogParser get(String filePath) {
        return boundParsers.getOrDefault(filePath, fallback);
    }

    /**
     * 文件是否已绑定 Parser
     */
    public boolean isBound(String filePath) {
        return boundParsers.containsKey(filePath);
    }

    /**
     * 文件被截断/轮转时清除绑定，下次读取重新探测
     */
    public void unbind(String filePath) {
        boundParsers.remove(filePath);
    }
}
