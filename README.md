# Log Monitor AI Platform

## 1. 系统概述

Log Monitor AI Platform 是一个基于微服务架构的智能日志监控平台，提供日志采集、AI 分析、规则告警的完整闭环。平台配套可嵌入 SDK（`log-monitor-starter`），支持 HTTP 和 MQ 双模式，其他业务服务只需引入依赖即可将日志上报到平台。

### 核心能力

- **日志采集**：WatchService 实时监控日志文件，解析标准/非标准格式，批量持久化
- **AI 智能分析**：消费日志消息，调用大模型生成问题摘要、根因分析和修复建议
- **规则告警**：关键词/正则匹配，支持静默时间、邮件通知、WebSocket 实时推送
- **实时可视化**：WebSocket 推送日志流和告警，前端 Dashboard 展示全局概览
- **SDK 接入**：`log-monitor-starter` 供其他服务零编码接入，双模式可选

## 2. 系统架构

### 2.1 架构图

```
┌──────────────────────────────────────────────────────────┐
│                     log-ai-frontend                       │
│               React 18 + TypeScript + Ant Design 5        │
└────────────────────┬─────────────────────────────────────┘
                     │ HTTP/WS
              ┌──────▼──────┐
              │   gateway    │  :8080  Spring Cloud Gateway
              │  JWT Auth    │
              └──┬───┬───┬──┘
                 │   │   │
    ┌────────────┘   │   └────────────┐
    ▼                ▼                ▼
┌──────────┐   ┌──────────┐   ┌──────────────┐
│log-service│  │ai-service│   │alert-service │
│  :8081    │  │  :8082   │   │   :8083      │
│ 日志采集  │  │ AI分析   │   │   规则告警    │
└──┬───┬────┘  └────┬─────┘   └──────┬───────┘
   │   │            │                │
   │   └────────────┼────────────────┘
   │          ┌─────▼─────┐
   │          │  RabbitMQ  │
   │          └─────┬─────┘
   │                │
   └──────┬─────────┘
    ┌─────▼─────┐
    │   MySQL    │
    └───────────┘

   ┌──────────────────┐
   │ 其他业务服务       │
   │ log-monitor-starter│  ← SDK 接入（HTTP 或 MQ）
   └──────────────────┘
```

### 2.2 技术栈

| 组件 | 技术选型 | 版本 |
|------|----------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| 微服务网关 | Spring Cloud Gateway | 2023.0.0 |
| 消息中间件 | RabbitMQ | — |
| ORM | MyBatis-Plus | 3.5.10 |
| 数据库 | MySQL | 8.x |
| 认证 | JJWT | 0.12.3 |
| JSON | FastJSON2 | 2.0.43 |
| 前端框架 | React + TypeScript | 18 + 5.6 |
| UI 组件库 | Ant Design | 5.22 |
| 构建工具 | Vite | 6.0 |
| Java 版本 | JDK 17 | — |
| 构建工具 | Maven | — |

## 3. 服务模块

### 3.1 gateway（API 网关）— 端口 8080

统一入口，JWT 认证，路由转发。

**路由规则：**

| 路由 ID | 目标服务 | 路径匹配 |
|---------|---------|----------|
| log-service | log-service:8081 | `/api/log/**` |
| ai-service | ai-service:8082 | `/api/ai/**` |
| alert-service | alert-service:8083 | `/api/alert/**` |
| log-websocket | log-service:8081 | `/ws/log/**` |
| alert-websocket | alert-service:8083 | `/ws/alert/**` |

**API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录认证，返回 JWT Token |

---

### 3.2 log-service（日志采集服务）— 端口 8081

日志文件监控、解析、存储、分发。

**核心功能：**
- WatchService 监控指定目录，增量读取日志文件
- 解析标准格式（时间+级别+线程+类名+内容）和自定义格式
- MyBatis-Plus 批量写入 MySQL
- 通过 RabbitMQ 分发日志到 AI 服务和告警服务
- WebSocket 实时推送日志流
- 死信队列处理失败消息

**API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/log/entries` | 分页查询日志（支持筛选） |
| GET | `/api/log/entries/{id}` | 查询单条日志详情 |
| POST | `/api/log/collect` | SDK HTTP 模式批量接收日志 |

**日志列表筛选参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `page` | int | 页码，默认 1 |
| `size` | int | 每页条数，默认 20 |
| `logLevel` | String | 日志级别（TRACE/DEBUG/INFO/WARN/ERROR/FATAL） |
| `className` | String | 类名模糊匹配 |
| `fileName` | String | 文件名模糊匹配 |
| `threadName` | String | 线程名模糊匹配 |
| `startTime` | ISO DateTime | 起始时间 |
| `endTime` | ISO DateTime | 结束时间 |
| `keyword` | String | 关键字（匹配内容、类名、文件名） |

**配置：**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `log.watch.directory` | — | 日志监控目录 |
| `log.watch.poll-interval-ms` | 500 | 轮询间隔（ms） |
| `log.watch.filter-self-logs` | true | 过滤自身组件日志，防止反馈循环 |
| `log.watch.self-log-classes` | 内置 5 个类 | 需过滤的自身组件类名 |

---

### 3.3 ai-service（AI 分析服务）— 端口 8082

消费日志消息，调用大模型进行智能分析。

**核心功能：**
- RabbitMQ 消费日志，prefetch=50，手动 ACK
- Prompt 模板构建，调用 AI API（支持替换实现）
- Redis 缓存分析结果（MD5 去重，默认 30 分钟）
- 分析结果存储到 `ai_analysis_result` 表
- 队列 TTL 10 分钟，最大长度 50,000

**API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/results` | 分页查询 AI 分析结果 |
| GET | `/api/ai/results/{id}` | 查询单条分析结果详情 |
| POST | `/api/ai/analyze/{logEntryId}` | 手动触发 AI 分析 |

---

### 3.4 alert-service（告警服务）— 端口 8083

基于规则的日志告警引擎。

**核心功能：**
- 规则引擎：CONTAINS / REGEX 两种匹配方式
- 支持按日志级别过滤、静默时间、频率控制
- 邮件通知（DingTalk 预留）
- WebSocket 实时推送告警
- 规则缓存（volatile + 定时刷新 30s）
- 队列 TTL 5 分钟，最大长度 100,000

**告警规则 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert/rules` | 分页查询告警规则 |
| GET | `/api/alert/rules/{id}` | 查询规则详情 |
| POST | `/api/alert/rules` | 创建规则（自动刷新缓存） |
| PUT | `/api/alert/rules/{id}` | 更新规则（自动刷新缓存） |
| DELETE | `/api/alert/rules/{id}` | 删除规则（自动刷新缓存） |

**告警记录 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert/records` | 分页查询告警记录（支持筛选） |
| GET | `/api/alert/records/{id}` | 查询告警记录详情 |

**告警记录筛选参数：** `ruleId`、`notifyStatus`、`startTime`、`endTime`

**规则字段：**

| 字段 | 说明 |
|------|------|
| `ruleName` | 规则名称 |
| `keyword` | 匹配关键词或正则表达式 |
| `logLevel` | 适用日志级别（NULL=全部） |
| `matchType` | CONTAINS / REGEX |
| `notifyType` | EMAIL / DINGTALK / ALL |
| `silenceMin` | 静默时间（分钟） |

---

### 3.5 log-monitor-starter（接入 SDK）

供其他业务服务引入，通过 HTTP 或 MQ 模式将日志上报到监控平台。

**Maven 坐标：** `com.logmonitor:log-monitor-starter`

**两种模式：**

| 模式 | 通信方式 | 适用场景 | 额外依赖 |
|------|---------|----------|---------|
| HTTP（默认） | POST 批量发送到 log-service | 无法/不愿引入 RabbitMQ | 无 |
| MQ | 直连 RabbitMQ Exchange | 高吞吐、异步解耦 | `spring-boot-starter-amqp` |

**使用方式：**

```java
// 1. 注入客户端
@Autowired
private LogMonitorClient logMonitorClient;

// 2. 发送日志
LogEntry entry = LogEntry.builder()
    .fileName("my-service.log")
    .logLevel("ERROR")
    .className("com.example.MyService")
    .content("处理失败: ...")
    .logTime(LocalDateTime.now())
    .build();
logMonitorClient.send(entry);

// 3. 优雅关闭时 flush
logMonitorClient.flush();
```

**配置示例：**

```yaml
# HTTP 模式（默认）
log-monitor:
  mode: http
  url: http://log-service:8081
  batch-size: 50

# MQ 模式
log-monitor:
  mode: mq
  exchange: log.monitor.log.exchange
  routing-key: log.new
```

**SDK 类：**

| 类 | 说明 |
|---|------|
| `LogMonitorClient` | 接口，`send(LogEntry)` + `flush()` |
| `HttpLogMonitorClient` | HTTP 实现，批量缓冲 + RestTemplate POST |
| `MqLogMonitorClient` | MQ 实现，逐条 RabbitTemplate 发送 |
| `LogMonitorProperties` | 配置属性，前缀 `log-monitor` |
| `LogMonitorAutoConfiguration` | Spring Boot 自动装配 |

---

### 3.6 common（公共模块）

跨服务共享的数据结构。

| 包 | 内容 |
|----|------|
| `entity` | LogEntry、AiAnalysisResult、AlertRule、AlertRecord |
| `enums` | LogLevel、MatchType、NotifyType |
| `exception` | GlobalExceptionHandler、BusinessException |
| `result` | Result<T> 统一返回体 |

## 4. RabbitMQ 消息设计

### 4.1 Exchange 与队列

```
log.monitor.log.exchange (TopicExchange)
    ├── routingKey: "log.new"
    │   ├── log.monitor.ai.queue      (ai-service 声明)
    │   └── log.monitor.alert.queue   (alert-service 声明)
    │
log.monitor.dlx.exchange (TopicExchange)
    └── routingKey: "#"
        └── log.monitor.dlx.queue     (log-service 声明)
```

> 队列由各自的消费服务负责声明，不再集中在 log-service，各服务可独立部署。

### 4.2 队列参数

| 队列 | TTL | 最大长度 | 消费者 |
|------|-----|----------|--------|
| `log.monitor.ai.queue` | 10 min | 50,000 | ai-service |
| `log.monitor.alert.queue` | 5 min | 100,000 | alert-service |
| `log.monitor.dlx.queue` | 无 | 无 | log-service (DlxMessageHandler) |

### 4.3 WebSocket

| 路径 | 服务 | 用途 |
|------|------|------|
| `/ws/log` | log-service | 实时日志流推送 |
| `/ws/alert` | alert-service | 实时告警推送 |

## 5. 数据库

### 5.1 数据表

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `log_entry` | 日志记录 | id, file_name, file_path, log_level, log_time, thread_name, class_name, content |
| `ai_analysis_result` | AI 分析结果 | id, log_entry_id, summary, root_cause, suggestion, model_name |
| `alert_rule` | 告警规则 | id, rule_name, keyword, log_level, match_type, notify_type, is_enabled, silence_min |
| `alert_record` | 告警记录 | id, rule_id, rule_name, log_entry_id, log_level, alert_content, notify_type, notify_status |
| `sys_user` | 系统用户 | id, username, password, role, is_enabled |

### 5.2 初始数据

- 管理员账号：`admin / admin123`
- 预置告警规则：ERROR 级别、OOM、NPE、数据库连接失败、超时异常

## 6. 部署

### 6.1 依赖服务

- MySQL 8.x
- RabbitMQ
- Redis（仅 ai-service 缓存使用）

### 6.2 启动顺序

1. MySQL + RabbitMQ + Redis
2. `log-service` (:8081)
3. `ai-service` (:8082)
4. `alert-service` (:8083)
5. `gateway` (:8080)
6. `log-ai-frontend` (`npm run dev`)

### 6.3 开发环境

- AI 服务内置 `StubAiApiClient`，不依赖外部 AI API 即可运行
- 前端开发服务器自动代理 `/api` 到 `http://localhost:8080`

## 7. 项目结构

```
log-ai/
├── pom.xml                          # Maven 父 POM
├── .docs/logs.sql                   # 数据库初始化脚本
├── common/                          # 公共模块（Entity/Enum/Result）
├── gateway/                         # API 网关（Spring Cloud Gateway + JWT）
├── log-service/                     # 日志采集服务（文件监控+MQ生产+死信处理）
├── ai-service/                      # AI 分析服务（大模型调用+Redis缓存）
├── alert-service/                   # 告警服务（规则引擎+邮件通知）
├── log-monitor-starter/             # 接入 SDK（HTTP/MQ 双模式）
├── log-ai-frontend/                 # 前端（React 18 + Ant Design 5 + Vite）
└── logs/                            # 本地日志文件目录
```

## 8. 扩展

- **AI 模型替换**：实现 `AiApiClient` 接口，对接 OpenAI / Qwen / DeepSeek 等
- **通知渠道**：`NotifyType` 枚举扩展，新增钉钉、企业微信、短信等
- **日志存储**：集成 Elasticsearch 实现全文检索
- **监控指标**：集成 Micrometer + Prometheus + Grafana
- **多租户**：日志/规则增加租户隔离字段

---
*文档最后更新：2026-05-13*
