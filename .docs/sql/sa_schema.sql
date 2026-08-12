-- ============================================================
-- 态势感知扩展表结构 (SA = Situational Awareness)
-- 在 log_entry_indexes.sql 基础上执行
-- ============================================================

-- 1. log_entry 表加安全字段（态势感知扩展）
ALTER TABLE log_entry ADD COLUMN src_ip VARCHAR(45) NULL COMMENT '源IP（攻击源/访问源）';
ALTER TABLE log_entry ADD COLUMN dst_ip VARCHAR(45) NULL COMMENT '目的IP（被访问目标）';
ALTER TABLE log_entry ADD COLUMN src_port INT NULL COMMENT '源端口';
ALTER TABLE log_entry ADD COLUMN dst_port INT NULL COMMENT '目的端口';
ALTER TABLE log_entry ADD COLUMN protocol VARCHAR(20) NULL COMMENT '网络协议: TCP/UDP/ICMP/HTTP';
ALTER TABLE log_entry ADD COLUMN action VARCHAR(20) NULL COMMENT '设备动作: allow/deny/blocked/drop';
ALTER TABLE log_entry ADD COLUMN severity INT NULL COMMENT '安全事件严重级别 0-10';

-- 安全字段索引（态势感知查询高频字段）
CREATE INDEX idx_log_entry_src_ip ON log_entry(src_ip);
CREATE INDEX idx_log_entry_severity ON log_entry(severity);
CREATE INDEX idx_log_entry_action_time ON log_entry(action, log_time);

-- 2. 规则表（规则引擎）
CREATE TABLE IF NOT EXISTS rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '规则名称',
    description VARCHAR(500) COMMENT '规则描述',
    rule_type VARCHAR(20) NOT NULL COMMENT '规则类型: THRESHOLD(阈值)/MATCH(匹配)/CORRELATION(关联)',
    condition_field VARCHAR(50) NOT NULL COMMENT '匹配字段: src_ip/log_level/action/service_name',
    condition_op VARCHAR(20) NOT NULL COMMENT '操作符: EQ(等于)/NE(不等于)/CONTAINS(包含)/GT(大于)/LT(小于)',
    condition_value VARCHAR(200) NOT NULL COMMENT '匹配值',
    threshold INT DEFAULT 1 COMMENT '触发阈值（窗口内命中次数）',
    time_window_sec INT DEFAULT 60 COMMENT '时间窗口（秒）',
    severity INT DEFAULT 5 COMMENT '告警严重级别 0-10',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用: 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='安全规则表';

-- 3. 告警表（规则触发产生）
CREATE TABLE IF NOT EXISTS alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT COMMENT '触发的规则ID',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    severity INT NOT NULL COMMENT '严重级别 0-10',
    src_ip VARCHAR(45) COMMENT '攻击源IP',
    log_entry_id BIGINT COMMENT '关联日志ID',
    content TEXT NOT NULL COMMENT '告警内容描述',
    status VARCHAR(20) DEFAULT 'OPEN' COMMENT '状态: OPEN(待处理)/ACK(已确认)/RESOLVED(已解决)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_severity (severity),
    INDEX idx_alert_status (status),
    INDEX idx_alert_create_time (create_time),
    INDEX idx_alert_src_ip (src_ip),
    UNIQUE KEY uk_alert_rule_entry (rule_id, log_entry_id)
) COMMENT='安全告警表';

-- 幂等兜底：同一规则对同一日志只生成一条告警（MATCH 类型），THRESHOLD 告警 log_entry_id 为 NULL 不受唯一约束影响
-- 存量环境（已建表）执行：ALTER TABLE alert ADD UNIQUE INDEX uk_alert_rule_entry (rule_id, log_entry_id);

-- 4. 预置规则（面试演示用）
INSERT INTO rule (name, description, rule_type, condition_field, condition_op, condition_value, threshold, time_window_sec, severity) VALUES
('暴力破解检测', '1分钟内同一源IP被防火墙拒绝5次以上', 'THRESHOLD', 'action', 'EQ', 'deny', 5, 60, 7),
('端口扫描检测', '日志内容包含端口扫描特征', 'MATCH', 'content', 'CONTAINS', 'port scan', 1, 60, 6),
('SQL注入检测', '日志内容包含SQL注入特征', 'MATCH', 'content', 'CONTAINS', "union select", 1, 60, 8),
('XSS攻击检测', '日志内容包含XSS攻击特征', 'MATCH', 'content', 'CONTAINS', '<script>', 1, 60, 8),
('ERROR日志激增', '5分钟内ERROR日志超过20条', 'THRESHOLD', 'log_level', 'EQ', 'ERROR', 20, 300, 5);

-- 5. 审计日志表（安全审计）
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) COMMENT '操作用户（服务间调用为 service）',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型: LOGIN/LOG_QUERY/ALERT_ACK/RULE_CREATE/AI_CHAT 等',
    params TEXT COMMENT '请求参数摘要（已脱敏截断）',
    result VARCHAR(10) NOT NULL COMMENT '结果: SUCCESS/FAIL',
    error_message VARCHAR(500) COMMENT '失败原因',
    ip VARCHAR(45) COMMENT '来源 IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_username (username),
    INDEX idx_audit_operation (operation),
    INDEX idx_audit_create_time (create_time)
) COMMENT='安全审计日志表';

-- 6. AI 对话历史表（按用户隔离）
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL COMMENT '归属用户（由登录态决定）',
    role VARCHAR(20) NOT NULL COMMENT '消息角色: user/assistant',
    content TEXT NOT NULL COMMENT '消息内容（用户问题或 AI 回答）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_history_username_time (username, create_time)
) COMMENT='AI 对话历史表';
