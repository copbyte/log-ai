package com.logmonitor.log.store;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 基于 H2 嵌入式数据库的偏移量存储实现
 * <p>
 * 表结构：file_offset(file_path PK, offset, updated_at)
 * 使用 MERGE INTO 实现 upsert（H2 兼容 MySQL 模式语法）。
 */
@Slf4j
@Repository
public class H2FileOffsetStore implements FileOffsetStore {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS file_offset (
                file_path VARCHAR(1000) PRIMARY KEY,
                offset BIGINT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String GET_OFFSET_SQL =
            "SELECT offset FROM file_offset WHERE file_path = ?";

    private static final String UPSERT_OFFSET_SQL = """
            MERGE INTO file_offset (file_path, offset, updated_at)
            KEY (file_path)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String DELETE_OFFSET_SQL =
            "DELETE FROM file_offset WHERE file_path = ?";

    private final JdbcTemplate jdbc;

    public H2FileOffsetStore(@Qualifier("offsetJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute(CREATE_TABLE_SQL);
        log.info("H2 file_offset table initialized");
    }

    @Override
    public long getOffset(String filePath) {
        try {
            Long offset = jdbc.queryForObject(GET_OFFSET_SQL, Long.class, filePath);
            return offset != null ? offset : 0L;
        } catch (Exception e) {
            log.warn("Get offset failed for {}, defaulting to 0: {}", filePath, e.getMessage());
            return 0L;
        }
    }

    @Override
    public void saveOffset(String filePath, long offset) {
        jdbc.update(UPSERT_OFFSET_SQL, filePath, offset);
    }

    @Override
    public void flush(Map<String, Long> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(UPSERT_OFFSET_SQL, offsets.entrySet(), 100,
                (ps, entry) -> {
                    ps.setString(1, entry.getKey());
                    ps.setLong(2, entry.getValue());
                });
    }

    @Override
    public void delete(String filePath) {
        jdbc.update(DELETE_OFFSET_SQL, filePath);
    }
}
