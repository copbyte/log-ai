-- ================================================
-- AI日志监控平台 数据库初始化脚本
-- 数据库：log_monitor
-- ================================================

CREATE DATABASE IF NOT EXISTS log_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE log_monitor;

-- ================================================
-- log-service：日志记录表
-- ================================================
CREATE TABLE log_entry (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name     VARCHAR(255) NOT NULL                COMMENT '来源文件名',
    file_path     VARCHAR(500) NOT NULL                COMMENT '来源文件完整路径',
    log_level     VARCHAR(10)  NOT NULL                COMMENT '日志级别 ERROR/WARN/INFO/DEBUG',
    log_time      DATETIME     NOT NULL                COMMENT '日志原始时间',
    thread_name   VARCHAR(100)                         COMMENT '线程名',
    class_name    VARCHAR(255)                         COMMENT '类名（含包路径）',
    content       TEXT         NOT NULL                COMMENT '日志完整内容',
    stack_trace   TEXT                                 COMMENT '异常堆栈（ERROR级别）',
    is_analyzed   TINYINT(1)   NOT NULL DEFAULT 0      COMMENT 'AI是否已分析 0否 1是',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    INDEX idx_log_level   (log_level),
    INDEX idx_log_time    (log_time),
    INDEX idx_file_name   (file_name),
    INDEX idx_is_analyzed (is_analyzed),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志记录表';


-- ================================================
-- ai-service：AI分析结果表
-- ================================================
CREATE TABLE ai_analysis_result (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    log_entry_id  BIGINT        NOT NULL                COMMENT '关联日志ID',
    log_level     VARCHAR(10)   NOT NULL                COMMENT '日志级别（冗余，方便查询）',
    summary       VARCHAR(500)  NOT NULL                COMMENT '问题摘要',
    root_cause    TEXT          NOT NULL                COMMENT '根本原因分析',
    suggestion    TEXT          NOT NULL                COMMENT '修复建议',
    model_name    VARCHAR(100)  NOT NULL                COMMENT '使用的模型名称',
    tokens_used   INT           NOT NULL DEFAULT 0      COMMENT '消耗token数',
    hit_cache     TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否命中缓存 0否 1是',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
    PRIMARY KEY (id),
    INDEX idx_log_entry_id (log_entry_id),
    INDEX idx_log_level    (log_level),
    INDEX idx_create_time  (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析结果表';


-- ================================================
-- alert-service：告警规则表
-- ================================================
CREATE TABLE alert_rule (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_name     VARCHAR(100) NOT NULL                COMMENT '规则名称',
    description   VARCHAR(255)                         COMMENT '规则描述',
    log_level     VARCHAR(10)                          COMMENT '匹配日志级别，NULL表示全级别',
    match_type    VARCHAR(10)  NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配方式 CONTAINS/REGEX',
    keyword       VARCHAR(500) NOT NULL                COMMENT '匹配关键词或正则表达式',
    file_name     VARCHAR(255)                         COMMENT '限定文件名，NULL表示所有文件',
    notify_type   VARCHAR(50)  NOT NULL                COMMENT '通知方式 EMAIL/DINGTALK/BOTH',
    silence_min   INT          NOT NULL DEFAULT 5      COMMENT '静默时间（分钟），同规则N分钟内只告警一次',
    is_enabled    TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用 0否 1是',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_is_enabled (is_enabled),
    INDEX idx_log_level  (log_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';


-- ================================================
-- alert-service：告警记录表
-- ================================================
CREATE TABLE alert_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_id         BIGINT       NOT NULL                COMMENT '触发的规则ID',
    rule_name       VARCHAR(100) NOT NULL                COMMENT '规则名称（冗余，规则删除后仍可查）',
    log_entry_id    BIGINT       NOT NULL                COMMENT '触发告警的日志ID',
    log_level       VARCHAR(10)  NOT NULL                COMMENT '日志级别',
    alert_content   TEXT         NOT NULL                COMMENT '告警内容（截取的日志片段）',
    notify_type     VARCHAR(50)  NOT NULL                COMMENT '通知方式',
    notify_status   VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT '通知状态 PENDING/SUCCESS/FAIL',
    notify_msg      VARCHAR(500)                         COMMENT '通知失败原因',
    notify_time     DATETIME                             COMMENT '实际通知时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警触发时间',
    PRIMARY KEY (id),
    INDEX idx_rule_id      (rule_id),
    INDEX idx_log_entry_id (log_entry_id),
    INDEX idx_notify_status(notify_status),
    INDEX idx_create_time  (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';


-- ================================================
-- log-service：死信消息记录表（MQ消费失败的消息在此记录，防止永久丢失）
-- ================================================
CREATE TABLE dlx_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    original_body   TEXT         NOT NULL                COMMENT '死信消息原始JSON内容',
    origin_queue    VARCHAR(100) NOT NULL                COMMENT '消息来源队列名称',
    fail_reason     VARCHAR(500)                         COMMENT '失败原因摘要',
    retry_count     INT          NOT NULL DEFAULT 3      COMMENT '消费者已重试次数',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态 PENDING待处理/RESOLVED已处理/IGNORED已忽略',
    log_entry_id    BIGINT                               COMMENT '关联的原始日志ID（可解析时填入）',
    log_content     TEXT                                 COMMENT '可解析的日志内容',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status      (status),
    INDEX idx_create_time (create_time),
    INDEX idx_origin_queue(origin_queue)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信消息记录表';

-- ================================================
-- gateway：系统用户表（JWT鉴权）
-- ================================================
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    password    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt加密）',
    nickname    VARCHAR(50)                          COMMENT '昵称',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 ADMIN/USER',
    is_enabled  TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用 0否 1是',
    last_login  DATETIME                             COMMENT '最后登录时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';


-- ================================================
-- 初始化数据
-- ================================================

-- 默认管理员账号（密码：admin123，BCrypt加密后）
INSERT INTO sys_user (username, password, nickname, role) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 'ADMIN');

-- 默认告警规则
INSERT INTO alert_rule (rule_name, description, log_level, match_type, keyword, notify_type, silence_min) VALUES
('ERROR级别全捕获',  '捕获所有ERROR日志',           'ERROR', 'CONTAINS', 'ERROR',              'EMAIL', 5),
('OOM异常告警',     '内存溢出异常',                  NULL,    'CONTAINS', 'OutOfMemoryError',   'BOTH',  10),
('NPE异常告警',     '空指针异常',                    NULL,    'CONTAINS', 'NullPointerException','EMAIL', 5),
('数据库连接失败',   '数据库连接相关异常',            NULL,    'CONTAINS', 'Connection refused',  'BOTH',  10),
('超时异常告警',    '请求或连接超时',                 NULL,    'CONTAINS', 'TimeoutException',   'EMAIL', 5);