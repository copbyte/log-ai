package com.logmonitor.log.store;

import java.util.Map;

/**
 * 文件偏移量存储接口
 * <p>
 * 记录每个被监听日志文件的已读位置（字节偏移量），
 * 进程重启后可从存储恢复，避免重复采集。
 */
public interface FileOffsetStore {

    /**
     * 获取文件已读偏移量；不存在返回 0
     */
    long getOffset(String filePath);

    /**
     * 保存单个文件偏移量（upsert）
     */
    void saveOffset(String filePath, long offset);

    /**
     * 批量保存偏移量（upsert）
     */
    void flush(Map<String, Long> offsets);

    /**
     * 删除指定文件的偏移量记录（文件被删除/轮转时调用）
     */
    void delete(String filePath);
}
