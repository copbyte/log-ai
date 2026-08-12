-- ================================================
-- AI日志分析平台 v2.0 数据库初始化脚本
-- 数据库：log_monitor
-- 基于 MCP Agent 架构，支持日志检索、异常聚类、根因定位
--
-- 完整初始化顺序（必须按序执行）：
--   1. logs.sql               基础表 + Mock 数据（本脚本）
--   2. log_entry_indexes.sql  查询复合索引
--   3. sa_schema.sql          态势感知扩展：安全字段/规则表/告警表/审计表/预置规则
-- ================================================

CREATE DATABASE IF NOT EXISTS log_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE log_monitor;

-- ================================================
-- log-service：日志记录表
-- ================================================
DROP TABLE IF EXISTS log_entry;
CREATE TABLE log_entry (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name     VARCHAR(255) NOT NULL                COMMENT '来源文件名',
    file_path     VARCHAR(500)                         COMMENT '来源文件完整路径',
    log_level     VARCHAR(10)  NOT NULL                COMMENT '日志级别 TRACE/DEBUG/INFO/WARN/ERROR/FATAL',
    log_time      DATETIME     NOT NULL                COMMENT '日志原始时间',
    thread_name   VARCHAR(100)                         COMMENT '线程名',
    class_name    VARCHAR(255)                         COMMENT '类名（含包路径）',
    content       TEXT         NOT NULL                COMMENT '日志完整内容',
    stack_trace   TEXT                                 COMMENT '异常堆栈（ERROR级别）',
    trace_id      VARCHAR(64)                          COMMENT '链路追踪 TraceID（SkyWalking 等）',
    service_name  VARCHAR(100)                         COMMENT '来源服务名',
    log_source    VARCHAR(10)  NOT NULL DEFAULT 'FILE' COMMENT '日志来源 FILE/HTTP/ELK/LOKI/MOCK',
    is_analyzed   TINYINT(1)   NOT NULL DEFAULT 0      COMMENT 'AI是否已分析 0否 1是',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    INDEX idx_log_level   (log_level),
    INDEX idx_log_time    (log_time),
    INDEX idx_file_name   (file_name),
    INDEX idx_trace_id    (trace_id),
    INDEX idx_service_name(service_name),
    INDEX idx_log_source  (log_source),
    INDEX idx_is_analyzed (is_analyzed),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志记录表';


-- ================================================
-- 初始化 Mock 测试数据（模拟微服务链路日志，含异常场景）
-- ================================================

-- 场景1：订单服务 NPE 异常（trace-001 链路）
INSERT INTO log_entry (file_name, file_path, log_level, log_time, thread_name, class_name, content, stack_trace, trace_id, service_name, log_source) VALUES
('order-service.log', '/logs/order-service.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 30 MINUTE), 'http-nio-8081-exec-1', 'com.logmonitor.order.controller.OrderController', '收到创建订单请求, orderId=ORD-20260717001, userId=U10001', NULL, 'trace-001', 'order-service', 'MOCK'),
('order-service.log', '/logs/order-service.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 29 MINUTE), 'http-nio-8081-exec-1', 'com.logmonitor.order.service.OrderService', '订单创建中, 调用库存服务扣减库存', NULL, 'trace-001', 'order-service', 'MOCK'),
('order-service.log', '/logs/order-service.log', 'ERROR', DATE_SUB(NOW(), INTERVAL 28 MINUTE), 'http-nio-8081-exec-1', 'com.logmonitor.order.service.OrderService', '订单处理失败: 空指针异常', 'java.lang.NullPointerException: Cannot invoke "com.logmonitor.inventory.model.Stock.getSkuId()" because "stock" is null
    at com.logmonitor.order.service.OrderService.createOrder(OrderService.java:87)
    at com.logmonitor.order.controller.OrderController.create(OrderController.java:45)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)', 'trace-001', 'order-service', 'MOCK');

-- 场景2：支付服务连接超时（trace-001 链路关联）
INSERT INTO log_entry (file_name, file_path, log_level, log_time, thread_name, class_name, content, stack_trace, trace_id, service_name, log_source) VALUES
('payment-service.log', '/logs/payment-service.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 29 MINUTE), 'http-nio-8082-exec-3', 'com.logmonitor.payment.controller.PaymentController', '收到支付请求, orderId=ORD-20260717001', NULL, 'trace-001', 'payment-service', 'MOCK'),
('payment-service.log', '/logs/payment-service.log', 'ERROR', DATE_SUB(NOW(), INTERVAL 28 MINUTE), 'http-nio-8082-exec-3', 'com.logmonitor.payment.service.PaymentService', '支付网关连接超时', 'java.net.SocketTimeoutException: connect timed out
    at java.base/java.net.Socket.connect(Socket.java:591)
    at com.logmonitor.payment.client.PaymentGatewayClient.call(PaymentGatewayClient.java:112)
    at com.logmonitor.payment.service.PaymentService.process(PaymentService.java:67)
    at com.logmonitor.payment.controller.PaymentController.pay(PaymentController.java:38)', 'trace-001', 'payment-service', 'MOCK');

-- 场景3：用户服务 OOM（trace-002 链路）
INSERT INTO log_entry (file_name, file_path, log_level, log_time, thread_name, class_name, content, stack_trace, trace_id, service_name, log_source) VALUES
('user-service.log', '/logs/user-service.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 15 MINUTE), 'http-nio-8083-exec-2', 'com.logmonitor.user.controller.UserController', '查询用户信息, userId=U10001', NULL, 'trace-002', 'user-service', 'MOCK'),
('user-service.log', '/logs/user-service.log', 'WARN',  DATE_SUB(NOW(), INTERVAL 14 MINUTE), 'http-nio-8083-exec-2', 'com.logmonitor.user.cache.UserCache', 'Redis 缓存未命中, 回查数据库', NULL, 'trace-002', 'user-service', 'MOCK'),
('user-service.log', '/logs/user-service.log', 'ERROR', DATE_SUB(NOW(), INTERVAL 14 MINUTE), 'http-nio-8083-exec-2', 'com.logmonitor.user.service.UserService', '内存不足，无法分配用户头像图片缓存', 'java.lang.OutOfMemoryError: Java heap space
    at com.logmonitor.user.service.UserService.loadAvatar(UserService.java:156)
    at com.logmonitor.user.service.UserService.getUserInfo(UserService.java:89)
    at com.logmonitor.user.controller.UserController.info(UserController.java:31)', 'trace-002', 'user-service', 'MOCK');

-- 场景4：数据库连接失败（trace-003 链路）
INSERT INTO log_entry (file_name, file_path, log_level, log_time, thread_name, class_name, content, stack_trace, trace_id, service_name, log_source) VALUES
('order-service.log', '/logs/order-service.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 5 MINUTE), 'http-nio-8081-exec-5', 'com.logmonitor.order.controller.OrderController', '收到查询订单列表请求', NULL, 'trace-003', 'order-service', 'MOCK'),
('order-service.log', '/logs/order-service.log', 'ERROR', DATE_SUB(NOW(), INTERVAL 5 MINUTE), 'http-nio-8081-exec-5', 'com.logmonitor.order.dao.OrderDao', '数据库连接失败: Connection refused', 'java.sql.SQLException: Connection refused: connect
    at com.alibaba.druid.pool.DruidDataSource.getConnection(DruidDataSource.java:1453)
    at com.logmonitor.order.dao.OrderDao.queryList(OrderDao.java:42)
    at com.logmonitor.order.service.OrderService.listOrders(OrderService.java:123)
    at com.logmonitor.order.controller.OrderController.list(OrderController.java:67)', 'trace-003', 'order-service', 'MOCK');

-- 场景5：无 traceId 的普通日志（模拟未接入链路追踪的服务）
INSERT INTO log_entry (file_name, file_path, log_level, log_time, thread_name, class_name, content, trace_id, service_name, log_source) VALUES
('app.log', '/logs/app.log', 'INFO',  DATE_SUB(NOW(), INTERVAL 2 MINUTE), 'main', 'com.logmonitor.Application', '应用启动完成, 端口 8081', NULL, 'log-service', 'FILE'),
('app.log', '/logs/app.log', 'WARN',  DATE_SUB(NOW(), INTERVAL 1 MINUTE), 'scheduling-1', 'com.logmonitor.task.CleanupTask', '清理任务执行耗时较长: 3521ms', NULL, 'log-service', 'FILE'),
('app.log', '/logs/app.log', 'DEBUG', DATE_SUB(NOW(), INTERVAL 30 SECOND), 'http-nio-8081-exec-8', 'com.logmonitor.log.controller.LogEntryController', '查询日志列表, page=1, size=20', NULL, 'log-service', 'FILE');
