// 消息角色
export type Role = 'user' | 'assistant'

// 工具调用结果类型（由后端 ChatClient 工具调用产生，包含在 assistant 消息中）
export type ToolResultKind = 'search' | 'trace' | 'cluster' | 'rootCause' | 'unknown'

// 单条消息
export interface Message {
  id: string
  role: Role
  content: string
  // 工具调用元数据（可选）：assistant 消息可能附带工具结果，前端用于增强展示
  toolCalls?: ToolCallInfo[]
  // 创建时间
  createdAt: number
  // 是否流式生成中
  pending?: boolean
  // 错误信息（请求失败时）
  error?: string
}

// 工具调用信息
export interface ToolCallInfo {
  name: string
  // 简化的参数摘要
  args?: Record<string, unknown>
  // 工具返回的原始字符串（由后端拼接在响应里，前端解析用）
  result?: string
}

// 快捷查询项
export interface QuickAction {
  key: string
  label: string
  prompt: string
  icon?: string
}

// 链路日志条目（用于 TraceTimeline 可视化）
export interface TraceLogItem {
  logTime: string
  logLevel: string
  serviceName?: string
  className?: string
  content: string
}

// 异常聚类条目（用于 ClusterChart 可视化）
export interface ClusterItem {
  key: string
  count: number
  sample?: string
  serviceName?: string
}
