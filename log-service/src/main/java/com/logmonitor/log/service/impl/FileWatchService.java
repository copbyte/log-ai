package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.parser.LogParser;
import com.logmonitor.log.parser.LogParserRegistry;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.store.FileOffsetStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志文件监听服务
 * <p>
 * 核心职责：监听多个目录 → 增量读取新行 → Parser 解析 → 批量入库。
 * 偏移量持久化到 H2，重启不丢；Parser 自动适配多种日志格式；
 * 单次 batch 有上限，防 OOM。
 */
@Slf4j
@Service
public class FileWatchService {

    /** TraceID 提取正则（公共逻辑，所有 Parser 共用） */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "(?:trace[_-]?id|tid)[:\\s]*[\\[\\(]?([a-zA-Z0-9.\\-]{8,64})[\\]\\)]?",
            Pattern.CASE_INSENSITIVE
    );

    /** 单次 batch 最大条数，防止 OOM */
    private static final int MAX_BATCH_SIZE = 1000;

    /** 单次读取最大行数，超出分多次 poll 读取，防止大文件首次入库 OOM */
    private static final int MAX_LINES_PER_READ = 5000;

    /** 首次读取时取前 N 行做 Parser 探测 */
    private static final int PROBE_SAMPLE_LINES = 10;

    /** 偏移量 flush 到 H2 的间隔（毫秒） */
    private static final long OFFSET_FLUSH_INTERVAL_MS = 10_000;

    private final LogEntryService logEntryService;
    private final LogBatchProcessor logBatchProcessor;
    private final LogParserRegistry parserRegistry;
    private final FileOffsetStore offsetStore;

    /** 内存偏移量缓存，定时 flush 到 H2，避免每行都查 DB */
    private final Map<String, Long> offsetCache = new ConcurrentHashMap<>();

    /** 多目录监听：每个目录一个 WatchService */
    private final List<WatchService> watchServices = new ArrayList<>();
    private final List<Path> watchPaths = new ArrayList<>();

    @Value("${log.watch.poll-interval-ms:500}")
    private long pollIntervalMs;

    /** 是否过滤监控平台自身组件的日志，单部署开启避免反馈循环，多部署可关闭 */
    @Value("${log.watch.filter-self-logs:true}")
    private boolean filterSelfLogs;

    /** 监控平台自身组件的全限定类名（逗号分隔），其产生的日志将不会被二次采集 */
    @Value("${log.watch.self-log-classes:com.logmonitor.log.service.impl.LogBatchProcessor,com.logmonitor.log.service.impl.FileWatchService}")
    private String selfLogClasses;

    private volatile Set<String> selfLogClassSet;

    public FileWatchService(LogEntryService logEntryService,
                            LogBatchProcessor logBatchProcessor,
                            LogParserRegistry parserRegistry,
                            FileOffsetStore offsetStore) {
        this.logEntryService = logEntryService;
        this.logBatchProcessor = logBatchProcessor;
        this.parserRegistry = parserRegistry;
        this.offsetStore = offsetStore;
    }

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

    /**
     * 启动多目录监听
     */
    public void startWatching(List<String> directories) throws IOException {
        for (String dir : directories) {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            WatchService ws = FileSystems.getDefault().newWatchService();
            path.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchServices.add(ws);
            watchPaths.add(path);
            log.info("FileWatchService started, watching directory: {}", path.toAbsolutePath());
        }
    }

    @Scheduled(fixedDelayString = "${log.watch.poll-interval-ms:500}")
    public void poll() {
        if (watchServices.isEmpty()) {
            return;
        }

        // 收集本轮所有待处理的日志条目，用于批量插入
        List<LogEntry> batch = new ArrayList<>();

        for (int i = 0; i < watchServices.size(); i++) {
            WatchService ws = watchServices.get(i);
            Path watchPath = watchPaths.get(i);

            WatchKey key = ws.poll();
            if (key == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path filename = (Path) event.context();
                Path filePath = watchPath.resolve(filename);

                try {
                    String abs = filePath.toAbsolutePath().toString();

                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        // 文件被删除/移走：清理 H2 偏移量记录、内存缓存、Parser 绑定，
                        // 防止长期运行后 H2 表与内存缓存无限增长
                        offsetStore.delete(abs);
                        offsetCache.remove(abs);
                        parserRegistry.unbind(abs);
                        log.info("File deleted, cleaned offset/parser binding: {}", abs);
                        continue;
                    }

                    if (Files.isDirectory(filePath) || !Files.isReadable(filePath)) {
                        continue;
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        // 新建文件：偏移量归零，清除 Parser 绑定
                        offsetCache.put(abs, 0L);
                        parserRegistry.unbind(abs);
                    }

                    readNewLines(filePath, batch);
                } catch (Exception e) {
                    log.error("Error processing file: {}", filePath, e);
                }
            }

            key.reset();
        }

        // 批量持久化：分批写入，每批最多 MAX_BATCH_SIZE 条
        if (!batch.isEmpty()) {
            persistBatch(batch);
        }
    }

    /**
     * 定时将内存偏移量 flush 到 H2，避免进程崩溃丢失
     */
    @Scheduled(fixedDelay = OFFSET_FLUSH_INTERVAL_MS)
    public void flushOffsets() {
        if (offsetCache.isEmpty()) {
            return;
        }
        try {
            offsetStore.flush(offsetCache);
            log.debug("Offsets flushed: {} files", offsetCache.size());
        } catch (Exception e) {
            log.warn("Flush offsets failed: {}", e.getMessage());
        }
    }

    /**
     * 分批写入数据库并触发批次后续处理
     */
    private void persistBatch(List<LogEntry> batch) {
        for (int i = 0; i < batch.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, batch.size());
            List<LogEntry> sub = new ArrayList<>(batch.subList(i, end));
            try {
                logEntryService.saveBatch(sub, sub.size());
                logBatchProcessor.processBatchAsync(sub);
            } catch (Exception e) {
                log.error("批量保存日志失败，本批{}条（{}-{}）", sub.size(), i, end, e);
            }
        }
    }

    /**
     * 读取文件新增的行，解析后放入批次缓冲区
     */
    private void readNewLines(Path filePath, List<LogEntry> buffer) throws IOException {
        String absolutePath = filePath.toAbsolutePath().toString();
        long lastOffset = offsetCache.computeIfAbsent(absolutePath, offsetStore::getOffset);

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength <= lastOffset) {
                if (fileLength < lastOffset) {
                    // 文件被截断/轮转，重置偏移量并清除 Parser 绑定
                    offsetCache.put(absolutePath, 0L);
                    offsetStore.saveOffset(absolutePath, 0L);
                    parserRegistry.unbind(absolutePath);
                    log.info("File truncated/rotated, reset offset: {}", absolutePath);
                }
                return;
            }

            raf.seek(lastOffset);
            String fileName = filePath.getFileName().toString();
            String pathStr = filePath.toString();

            // 读取本次新增行（带编码转换 + 行数上限，防 OOM）
            List<String> lines = new ArrayList<>();
            String raw;
            while ((raw = raf.readLine()) != null && lines.size() < MAX_LINES_PER_READ) {
                String decoded = new String(raw.getBytes("ISO-8859-1"), "UTF-8");
                if (!decoded.isBlank()) {
                    lines.add(decoded);
                }
            }

            // 首次读取：取前 N 行做 Parser 探测，绑定后才解析所有行
            if (!parserRegistry.isBound(absolutePath) && !lines.isEmpty()) {
                List<String> sample = lines.subList(0, Math.min(PROBE_SAMPLE_LINES, lines.size()));
                parserRegistry.bind(absolutePath, sample);
            }

            // 用绑定的 Parser 解析所有行
            for (String line : lines) {
                LogEntry entry = parseLine(fileName, line, pathStr, absolutePath);
                if (entry != null) {
                    if (filterSelfLogs && isSelfLog(entry)) {
                        continue;
                    }
                    buffer.add(entry);
                }
            }

            offsetCache.put(absolutePath, raf.getFilePointer());
        }
    }

    /**
     * 使用绑定的 Parser 解析行，并补充公共字段（filePath/traceId/serviceName/logSource）
     */
    private LogEntry parseLine(String fileName, String line, String filePath, String absolutePath) {
        LogParser parser = parserRegistry.get(absolutePath);
        LogEntry entry = parser.parse(fileName, line);
        if (entry == null) {
            return null;
        }
        entry.setFilePath(filePath);
        entry.setLogSource("FILE");
        entry.setTraceId(extractTraceId(entry.getContent()));
        entry.setServiceName(extractServiceName(fileName));
        return entry;
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
