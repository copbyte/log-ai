package com.logmonitor.log.config;

import com.logmonitor.log.service.impl.FileWatchService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 日志文件监听启动配置
 * <p>
 * 支持多目录配置：
 * - 推荐用 log.watch.directories（数组）
 * - 兼容旧配置 log.watch.directory（单值），两者都配时以 directories 为准
 */
@Slf4j
@Configuration
public class FileWatchConfig {

    /** 新配置：多目录数组 */
    @Value("${log.watch.directories:}")
    private String directoriesConfig;

    /** 旧配置：单目录（向后兼容） */
    @Value("${log.watch.directory:}")
    private String legacyDirectory;

    private final FileWatchService fileWatchService;

    public FileWatchConfig(FileWatchService fileWatchService) {
        this.fileWatchService = fileWatchService;
    }

    @PostConstruct
    public void init() throws IOException {
        List<String> dirs = resolveDirectories();
        if (dirs.isEmpty()) {
            log.warn("No log.watch.directories configured, FileWatchService will be idle");
            return;
        }
        fileWatchService.startWatching(dirs);
    }

    /**
     * 解析监听目录：优先 directories，回退到 legacy directory
     */
    private List<String> resolveDirectories() {
        List<String> dirs = new ArrayList<>();

        if (directoriesConfig != null && !directoriesConfig.isBlank()) {
            Arrays.stream(directoriesConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(dirs::add);
        }

        if (dirs.isEmpty() && legacyDirectory != null && !legacyDirectory.isBlank()) {
            log.warn("log.watch.directory is deprecated, please use log.watch.directories (comma-separated)");
            dirs.add(legacyDirectory.trim());
        }

        return dirs;
    }
}
