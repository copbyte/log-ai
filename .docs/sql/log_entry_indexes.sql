-- ================================================
-- log_entry 表复合索引补丁
-- 用途：优化 AI Tool 查询性能，避免全表扫描
-- 执行方式：在 MySQL 的 log_monitor 库手动执行
-- ================================================

USE log_monitor;

-- 复合索引1：按服务+级别+时间范围查（LogAnalysisTools.searchLogs 高频场景）
-- 例：WHERE service_name=? AND log_level=? ORDER BY log_time DESC
CREATE INDEX idx_service_level_time ON log_entry(service_name, log_level, log_time);

-- 复合索引2：按级别+时间查（clusterExceptions 场景：查最近 N 条 ERROR）
-- 例：WHERE log_level='ERROR' ORDER BY log_time DESC LIMIT 100
CREATE INDEX idx_level_time ON log_entry(log_level, log_time);

-- 复合索引3：按时间倒序分页查（默认分页场景）
-- 例：ORDER BY id DESC（已有主键索引，此索引可选）
-- 已有 PRIMARY KEY(id) 覆盖，不再重复创建

-- 验证索引创建结果
SHOW INDEX FROM log_entry;
