package com.logmonitor.log.config;

import com.logmonitor.log.service.impl.FileWatchService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FileWatchConfig {

    @Value("${log.watch.directory:./logs}")
    private String watchDirectory;

    private final FileWatchService fileWatchService;

    public FileWatchConfig(FileWatchService fileWatchService) {
        this.fileWatchService = fileWatchService;
    }

    @PostConstruct
    public void init() throws IOException {
        fileWatchService.startWatching(watchDirectory);
    }
}
