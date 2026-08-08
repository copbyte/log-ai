package com.logmonitor.log.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ES 存量数据回填：把 MySQL 中的历史日志按 id 升序分页写入 ES。
 * <p>
 * 生产启用 ES 双写前，MySQL 已存在历史数据，必须回填一次才能让 ES 查询覆盖全量；
 * 文档 id 使用日志主键，重复执行是幂等的（覆盖写入，不产生重复）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsBackfillService {

    private static final int DEFAULT_PAGE_SIZE = 500;

    private final LogEntryMapper logEntryMapper;
    private final EsLogWriter esLogWriter;

    /** 全量回填，返回已写入 ES 的条数 */
    public long backfill(int pageSize) {
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        long total = 0;
        long page = 1;
        while (true) {
            Page<LogEntry> result = logEntryMapper.selectPage(
                    new Page<>(page, size),
                    new LambdaQueryWrapper<LogEntry>().orderByAsc(LogEntry::getId));
            List<LogEntry> records = result.getRecords();
            if (records.isEmpty()) {
                break;
            }
            esLogWriter.write(records);
            total += records.size();
            if (records.size() < size) {
                break;
            }
            page++;
        }
        log.info("ES 存量回填完成，共写入 {} 条", total);
        return total;
    }
}
