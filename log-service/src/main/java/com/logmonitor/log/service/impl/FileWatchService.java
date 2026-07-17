package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.service.LogEntryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileWatchService {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+\\s+\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+(\\S+)\\s+-\\s+(.*)$"
    );

    /** 从日志内容中提取 TraceID（兼容 SkyWalking TID、通用 traceId 格式） */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "(?:trace[_-]?id|tid)[:\\s]*[\\[\\(]?([a-zA-Z0-9.\\-]{8,64})[\\]\\)]?",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, Long> fileOffsets = new ConcurrentHashMap<>();
    private final LogEntryService logEntryService;
    private final LogBatchProcessor logBatchProcessor;
    private WatchService watchService;
    private Path watchPath;

    @Value("${log.watch.poll-interval-ms:500}")
    private long pollIntervalMs;

    /** 是否过滤监控平台自身组件的日志，单部署开启避免反馈循环，多部署可关闭 */
    @Value("${log.watch.filter-self-logs:true}")
    private boolean filterSelfLogs;

    /** 监控平台自身组件的全限定类名（逗号分隔），其产生的日志将不会被二次采集 */
    @Value("${log.watch.self-log-classes:com.logmonitor.log.service.impl.LogBatchProcessor,com.logmonitor.log.service.impl.FileWatchService}")
    private String selfLogClasses;

    private volatile Set<String> selfLogClassSet;

    private Set<String> getSelfLogClassSet() {
        if (selfLogClassSet == null) {
            selfLogClassSet = Collections.unmodifiableSet(
                    java.util.Arrays.stream(selfLogClasses.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet()));
        }
        return selfLogClassSet;
    }

    public FileWatchService(LogEntryService logEntryService,
                            LogBatchProcessor logBatchProcessor) {
        this.logEntryService = logEntryService;
        this.logBatchProcessor = logBatchProcessor;
    }

    public void startWatching(String directory) throws IOException {
        this.watchPath = Paths.get(directory);
        if (!Files.exists(watchPath)) {
            Files.createDirectories(watchPath);
        }
        this.watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        log.info("FileWatchService started, watching directory: {}", watchPath.toAbsolutePath());
    }

    @Scheduled(fixedDelayString = "${log.watch.poll-interval-ms:500}")
    public void poll() {
        if (watchService == null) {
            return;
        }

        WatchKey key = watchService.poll();
        if (key == null) {
            return;
        }

        // 收集本轮所有待处理的日志条目，用于MyBatis-Plus批量插入
        List<LogEntry> batch = new ArrayList<>();

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }

            Path filename = (Path) event.context();
            Path filePath = watchPath.resolve(filename);

            try {
                if (Files.isDirectory(filePath) || !Files.isReadable(filePath)) {
                    continue;
                }

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    fileOffsets.put(filePath.toString(), 0L);
                }

                // 读取新行并收集到批次列表中，不在此处持久化
                readNewLines(filePath, batch);
            } catch (Exception e) {
                log.error("Error processing file: {}", filePath, e);
            }
        }

        key.reset();

        // 批量持久化：使用MyBatis-Plus的saveBatch一次性写入数据库，大幅减少数据库交互次数
        if (!batch.isEmpty()) {
            try {
                logEntryService.saveBatch(batch, 100);
                // 异步处理批次后续逻辑，不阻塞下一次轮询
                logBatchProcessor.processBatchAsync(batch);
            } catch (Exception e) {
                log.error("批量保存日志失败，共{}条", batch.size(), e);
            }
        }
    }

    /**
     * 读取文件新增的行，解析后放入批次缓冲区
     * <p>
     * 仅做解析和收集，不做任何持久化或IO操作，
     * 解析结果统一由poll()方法批量处理。
     *
     * @param filePath 被监控的日志文件路径
     * @param buffer   日志条目收集缓冲区
     */
    private void readNewLines(Path filePath, List<LogEntry> buffer) throws IOException {
        String absolutePath = filePath.toAbsolutePath().toString();
        long lastOffset = fileOffsets.getOrDefault(absolutePath, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength <= lastOffset) {
                if (fileLength < lastOffset) {
                    fileOffsets.put(absolutePath, 0L);
                }
                return;
            }

            raf.seek(lastOffset);
            String line;
            String fileName = filePath.getFileName().toString();
            String pathStr = filePath.toString();
            while ((line = raf.readLine()) != null) {
                line = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                LogEntry entry = buildEntry(fileName, line, pathStr);
                if (entry != null) {
                    if (filterSelfLogs && isSelfLog(entry)) {
                        continue;
                    }
                    buffer.add(entry);
                }
            }

            fileOffsets.put(absolutePath, raf.getFilePointer());
        }
    }

    /**
     * 解析日志行并构建LogEntry对象
     * <p>
     * 仅负责解析和构建，不做持久化或发送操作。
     * 将解析与IO处理分离，便于批量收集后统一处理。
     *
     * @param fileName 日志文件名
     * @param line     日志行原始内容
     * @param filePath 日志文件完整路径
     * @return 解析后的LogEntry对象，空行则返回null
     */
    private LogEntry buildEntry(String fileName, String line, String filePath) {
        if (line.isBlank()) {
            return null;
        }

        LogEntry entry = parseLine(fileName, line);
        if (entry == null) {
            entry = fallbackParse(fileName, line);
        }

        entry.setFilePath(filePath);
        entry.setLogSource("FILE");
        entry.setTraceId(extractTraceId(entry.getContent()));
        entry.setServiceName(extractServiceName(fileName));
        return entry;
    }

    private LogEntry parseLine(String fileName, String line) {
        Matcher m = LOG_PATTERN.matcher(line);
        if (!m.matches()) {
            return null;
        }
        return LogEntry.builder()
                .fileName(fileName)
                .logTime(parseTimestamp(m.group(1)))
                .logLevel(m.group(2))
                .threadName(m.group(3))
                .className(m.group(4))
                .content(m.group(5))
                .build();
    }

    private LogEntry fallbackParse(String fileName, String line) {
        String logLevel = "INFO";
        String upper = line.toUpperCase();
        if (upper.contains("FATAL")) logLevel = "FATAL";
        else if (upper.contains("ERROR")) logLevel = "ERROR";
        else if (upper.contains("WARN")) logLevel = "WARN";
        else if (upper.contains("DEBUG")) logLevel = "DEBUG";
        else if (upper.contains("TRACE")) logLevel = "TRACE";

        return LogEntry.builder()
                .fileName(fileName)
                .logTime(LocalDateTime.now())
                .logLevel(logLevel)
                .content(line)
                .threadName(null)
                .build();
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }

    /** 从日志内容中提取 TraceID */
    private String extractTraceId(String content) {
        if (content == null) {
            return null;
        }
        Matcher m = TRACE_ID_PATTERN.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    /** 从文件名推断服务名（去掉 .log 后缀） */
    private String extractServiceName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.endsWith(".log") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    /**
     * 判断日志条目是否来自监控平台自身组件，防止单部署下的反馈循环
     */
    private boolean isSelfLog(LogEntry entry) {
        String className = entry.getClassName();
        return className != null && getSelfLogClassSet().contains(className);
    }
}
