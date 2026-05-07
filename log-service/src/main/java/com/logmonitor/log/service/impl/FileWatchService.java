package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mq.producer.LogEntryProducer;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.websocket.LogWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FileWatchService {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+(\\S+)\\s+(\\S+)\\s+-\\s+(.*)$"
    );

    private final Map<String, Long> fileOffsets = new ConcurrentHashMap<>();
    private final LogEntryService logEntryService;
    private final LogEntryProducer logEntryProducer;
    private final LogWebSocketHandler webSocketHandler;
    private WatchService watchService;
    private Path watchPath;

    @Value("${log.watch.poll-interval-ms:500}")
    private long pollIntervalMs;

    public FileWatchService(LogEntryService logEntryService,
                            LogEntryProducer logEntryProducer,
                            LogWebSocketHandler webSocketHandler) {
        this.logEntryService = logEntryService;
        this.logEntryProducer = logEntryProducer;
        this.webSocketHandler = webSocketHandler;
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

                readNewLines(filePath);
            } catch (Exception e) {
                log.error("Error processing file: {}", filePath, e);
            }
        }

        key.reset();
    }

    private void readNewLines(Path filePath) throws IOException {
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
            while ((line = raf.readLine()) != null) {
                line = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                processLine(filePath.getFileName().toString(), line);
            }

            fileOffsets.put(absolutePath, raf.getFilePointer());
        }
    }

    private void processLine(String fileName, String line) {
        if (line.isBlank()) {
            return;
        }

        LogEntry entry = parseLine(fileName, line);
        if (entry == null) {
            entry = fallbackParse(fileName, line);
        }

        try {
            logEntryService.save(entry);
            webSocketHandler.broadcastLogEntry(entry);
            logEntryProducer.send(entry);
            log.debug("Processed log: {} [{}] {}", fileName, entry.getLogLevel(), entry.getContent());
        } catch (Exception e) {
            log.error("Failed to process log line: {}", line, e);
        }
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
                .content(m.group(6))
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
                .threadName(Thread.currentThread().getName())
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
}
