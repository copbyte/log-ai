# Log Monitor AI Platform - 详细文档

## 1. 系统概述

Log Monitor AI Platform 是一个基于微服务架构的智能日志监控平台，旨在帮助企业实现日志的自动化收集、实时分析、智能告警和问题诊断。系统采用 Spring Boot 3.x 构建，通过消息队列解耦各服务组件，支持高并发日志处理和实时响应。

### 核心价值
- **AI智能分析**：集成大模型能力，自动分析日志内容，提供问题摘要、根因分析和修复建议
- **实时告警**：基于灵活的规则引擎，支持关键词匹配和正则表达式，支持多种通知渠道
- **全链路监控**：从日志采集、存储、分析到告警的完整闭环
- **实时可视化**：通过 WebSocket 实现实时日志流和告警推送
- **可扩展架构**：微服务设计，各组件可独立部署和扩展

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   log-service   │    │    ai-service   │    │  alert-service  │
│ (日志采集服务)  │    │ (AI分析服务)    │    │ (告警服务)      │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                        │                        │
         └────────────────────────────────────────────────┘
                              │
                      ┌───────▼───────┐
                      │  RabbitMQ     │
                      │ (消息中间件)  │
                      └───────┬───────┘
                              │
                      ┌───────▼───────┐
                      │   gateway     │
                      │ (API网关)     │
                      └───────┬───────┘
                              │
                      ┌───────▼───────┐
                      │   Web Client  │
                      │ (前端应用)    │
                      └───────────────┘
```

### 2.2 技术栈

| 组件 | 技术选型 | 版本 |
|------|----------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| 微服务治理 | Spring Cloud | 2023.0.0 |
| 消息中间件 | RabbitMQ | - |
| 数据库 | MySQL + H2 | MySQL 8.x + H2 2.2.224 |
| 缓存 | Redis | - |
| API文档 | SpringDoc OpenAPI | - |
| 构建工具 | Maven | - |
| Java版本 | JDK 17 | - |

## 3. 服务模块详解

### 3.1 log-service（日志采集服务）

**端口**: 8081
**职责**: 日志文件监控、解析、存储和分发

#### 核心功能
- **文件监控**: 使用 WatchService 监控指定目录下的日志文件变化
- **日志解析**: 支持标准日志格式（时间戳+级别+线程+类名+内容）和自定义格式
- **数据存储**: 将解析后的日志存储到 MySQL 数据库 `log_entry` 表中
- **消息分发**: 通过 RabbitMQ 将新日志发送给 AI 分析服务和告警服务
- **实时推送**: 通过 WebSocket 将新日志实时推送到前端客户端

#### 配置参数
- `log.watch.directory`: 日志监控目录（默认: ./logs）
- `log.watch.poll-interval-ms`: 监控轮询间隔（默认: 500ms）

#### API接口
- `GET /api/log/entries`: 分页查询日志列表
- `GET /api/log/entries/{id}`: 查询单条日志详情

### 3.2 ai-service（AI分析服务）

**端口**: 8082
**职责**: 日志的智能分析和问题诊断

#### 核心功能
- **AI分析**: 接收日志消息，调用大模型API进行智能分析
- **缓存机制**: 使用 Redis 缓存分析结果，避免重复分析相同内容
- **结果存储**: 将分析结果（摘要、根因、建议）存储到数据库
- **实时推送**: 通过 WebSocket 将分析结果推送到前端

#### 分析流程
1. 接收日志消息（RabbitMQ）
2. 计算日志内容MD5哈希值
3. 查询Redis缓存是否已存在相同内容的分析结果
4. 如未命中缓存，则构建Prompt并调用AI API
5. 存储分析结果到数据库
6. 缓存分析结果30分钟

#### API接口
- `GET /api/ai/results`: 分页查询AI分析结果
- `GET /api/ai/results/{id}`: 查询单条分析结果详情
- `POST /api/ai/analyze/{logEntryId}`: 手动触发AI分析

### 3.3 alert-service（告警服务）

**端口**: 8083
**职责**: 基于规则的日志告警和通知

#### 核心功能
- **规则引擎**: 支持多种匹配方式（CONTAINS/REGEX）和条件组合
- **告警策略**: 支持静默时间、告警频率控制
- **多渠道通知**: Email、钉钉（DingTalk）、Websocket实时推送
- **告警记录**: 完整记录告警触发过程和状态

#### 规则配置
- `ruleName`: 规则名称
- `keyword`: 匹配关键词或正则表达式
- `logLevel`: 日志级别过滤（NULL表示所有级别）
- `matchType`: 匹配类型（CONTAINS/REGEX）
- `notifyType`: 通知方式（EMAIL/DINGTALK/ALL）
- `silenceMin`: 静默时间（分钟）

#### API接口
- `GET /api/alert/rules`: 分页查询告警规则
- `GET /api/alert/rules/{id}`: 查询单条规则详情
- `POST /api/alert/rules`: 创建新规则
- `PUT /api/alert/rules/{id}`: 更新规则
- `DELETE /api/alert/rules/{id}`: 删除规则
- `GET /api/alert/records`: 分页查询告警记录

### 3.4 gateway（API网关）

**端口**: 8080
**职责**: 统一API入口、路由转发、JWT认证

#### 路由配置
| 路由ID | 目标服务 | 路径匹配 | URI |
|--------|----------|----------|-----|
| log-service | log-service | `/api/log/**` | `http://localhost:8081` |
| ai-service | ai-service | `/api/ai/**` | `http://localhost:8082` |
| alert-service | alert-service | `/api/alert/**` | `http://localhost:8083` |
| log-websocket | log-service | `/ws/log/**` | `http://localhost:8081` |
| alert-websocket | alert-service | `/ws/alert/**` | `http://localhost:8083` |

#### JWT认证
- `jwt.secret`: JWT密钥（开发环境使用固定密钥）
- `jwt.expiration`: Token有效期（86400000ms = 24小时）

### 3.5 common（公共模块）

**职责**: 提供跨服务共享的数据结构、枚举和工具类

#### 核心DTO/Entity
- `LogEntryDTO`: 日志条目传输对象
- `AiAnalysisResultDTO`: AI分析结果传输对象
- `AlertRuleDTO`: 告警规则传输对象
- `AlertRecordDTO`: 告警记录传输对象

#### 核心枚举
- `LogLevel`: 日志级别（TRACE/DEBUG/INFO/WARN/ERROR/FATAL）
- `MatchType`: 匹配类型（CONTAINS/REGEX）
- `NotifyType`: 通知类型（EMAIL/DINGTALK/ALL）

## 4. 数据流与集成模式

### 4.1 主要数据流

```
1. 日志采集流程:
   文件监控 → 解析日志 → 存储到MySQL → 发送MQ消息 → WebSocket推送

2. AI分析流程:
   MQ接收日志 → Redis缓存检查 → 构建Prompt → 调用AI API → 存储结果 → WebSocket推送

3. 告警处理流程:
   MQ接收日志 → 查询启用规则 → 规则匹配 → 创建告警记录 → 发送通知 → WebSocket推送
```

### 4.2 RabbitMQ消息队列设计

| Exchange | Queue | Routing Key | 用途 |
|----------|-------|-------------|------|
| `log.monitor.log.exchange` | `log.monitor.ai.queue` | `log.new` | 日志分发给AI分析服务 |
| `log.monitor.log.exchange` | `log.monitor.alert.queue` | `log.new` | 日志分发给告警服务 |
| `log.monitor.dlx.exchange` | `log.monitor.dlx.queue` | `#` | 死信队列，处理失败消息 |

### 4.3 WebSocket实时通信

| WebSocket路径 | 服务 | 用途 |
|---------------|------|------|
| `/ws/log` | log-service | 实时日志流推送 |
| `/ws/alert` | alert-service | 实时告警推送 |

## 5. 数据库设计

### 5.1 主要数据表

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `log_entry` | 日志记录表 | id, file_name, log_level, log_time, content, create_time |
| `ai_analysis_result` | AI分析结果表 | id, log_entry_id, summary, root_cause, suggestion, model_name |
| `alert_rule` | 告警规则表 | id, rule_name, keyword, log_level, match_type, notify_type, is_enabled |
| `alert_record` | 告警记录表 | id, rule_id, log_entry_id, alert_content, notify_status, create_time |
| `sys_user` | 系统用户表 | id, username, password, role, is_enabled |

### 5.2 初始化数据

- 默认管理员账号: `admin/admin123`
- 预置告警规则: ERROR级别捕获、OOM异常、NPE异常、数据库连接失败、超时异常

## 6. 部署与运行

### 6.1 依赖服务
- MySQL 8.x (用于生产环境)
- H2 Database (嵌入式，用于开发测试)
- Redis (缓存服务)
- RabbitMQ (消息中间件)

### 6.2 启动顺序
1. 启动 RabbitMQ
2. 启动 Redis
3. 启动 MySQL
4. 启动 gateway (端口 8080)
5. 启动 log-service (端口 8081)
6. 启动 ai-service (端口 8082)
7. 启动 alert-service (端口 8083)

### 6.3 开发环境配置
- 数据库: H2 内存数据库
- AI服务: 使用 StubAiApiClient 模拟AI分析
- 消息队列: RabbitMQ 本地实例

## 7. 扩展性与定制化

### 7.1 AI服务扩展
- 可替换 `AiApiClient` 实现，集成真实的大模型API（如OpenAI、Qwen等）
- 可调整 `PromptServiceImpl` 中的Prompt模板以适应不同模型
- 可配置不同的缓存策略和过期时间

### 7.2 告警服务扩展
- 可添加新的通知渠道（短信、企业微信等）
- 可扩展规则引擎支持更复杂的条件表达式
- 可集成外部告警系统（Prometheus Alertmanager等）

### 7.3 日志服务扩展
- 可支持更多日志格式解析器
- 可集成ELK Stack进行日志聚合
- 可添加日志归档和清理策略

## 8. 安全考虑

- **认证授权**: JWT Token 认证，支持角色权限控制
- **数据加密**: 密码使用 BCrypt 加密存储
- **输入验证**: 所有API接口使用JSR-303验证
- **SQL注入防护**: 使用 MyBatis-Plus 参数化查询
- **XSS防护**: 前端需要对日志内容进行HTML转义

## 9. 未来发展方向

- **多租户支持**: 添加租户隔离机制
- **性能监控**: 集成Micrometer和Prometheus监控指标
- **日志搜索**: 集成Elasticsearch实现全文检索
- **机器学习**: 基于历史日志训练异常检测模型
- **可视化仪表盘**: 集成Grafana展示关键指标
- **移动端支持**: 开发iOS/Android客户端

## 10. 项目结构

```
log-ai/
├── pom.xml                    # Maven父POM
├── .docs/
│   └── logs.sql               # 数据库初始化脚本
├── common/                    # 公共模块
│   ├── src/main/java/com/logmonitor/common/
│   │   ├── dto/               # 数据传输对象
│   │   ├── entity/            # 数据实体
│   │   ├── enums/             # 枚举类型
│   │   ├── exception/         # 自定义异常
│   │   └── result/            # 统一返回结果
├── gateway/                   # API网关
├── log-service/               # 日志采集服务
├── ai-service/                # AI分析服务
├── alert-service/             # 告警服务
└── .idea/                     # IDE配置
```

---
*文档最后更新: 2026年5月8日*
