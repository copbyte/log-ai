export interface LogEntry {
  id: number
  fileName: string
  filePath?: string
  logLevel: string
  logTime: string
  threadName?: string
  className?: string
  content: string
  createTime: string
  updateTime?: string
}

export interface AiAnalysisResult {
  id: number
  logEntryId: number
  summary: string
  rootCause: string
  suggestion: string
  modelName: string
  tokensUsed: number
  createTime: string
}

export interface AlertRule {
  id?: number
  ruleName: string
  keyword: string
  logLevel: string | null
  matchType: 'CONTAINS' | 'REGEX'
  notifyType: 'EMAIL' | 'DINGTALK' | 'ALL'
  isEnabled: boolean
}

export interface AlertRecord {
  id: number
  ruleId: number
  logEntryId: number
  alertContent: string
  notifyStatus: string
  createTime: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  role: string
}