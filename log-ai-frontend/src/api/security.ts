// 安全态势告警相关接口（与后端 AlertController 对应，端口 8081）

/** 告警状态 */
export type AlertStatus = 'OPEN' | 'ACK' | 'RESOLVED'

/** 告警条目（与后端 Alert 实体对应） */
export interface AlertItem {
  id: number
  ruleId?: number
  ruleName: string
  severity: number
  srcIp?: string
  logEntryId?: number
  content: string
  status: AlertStatus
  createTime: string
}

/** 告警统计 */
export interface AlertStats {
  total: number
  openCount: number
  ackCount: number
  resolvedCount: number
  // 按严重级别
  criticalCount: number // severity >= 8
  highCount: number // severity 5-7
  mediumCount: number // severity 3-4
  lowCount: number // severity 1-2
  // 最近24小时趋势 [{hour: "2026-07-19 14:00", count: 5}]
  trend: Array<{ hour: string; count: number }>
}

/** 告警列表查询参数 */
export interface FetchAlertsParams {
  status?: string
  severity?: number
  page?: number
  size?: number
}

/** 后端统一响应体 { code, message, data } */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

const BASE_URL = 'http://localhost:8081'

/**
 * 统一请求封装：解析后端 { code, message, data } 结构
 * 约定 code === 0 或 200 视为成功，否则抛出业务错误
 */
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const resp = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!resp.ok) {
    throw new Error(`请求失败: ${resp.status} ${resp.statusText}`)
  }
  const json: ApiResult<T> = await resp.json()
  if (json.code !== 0 && json.code !== 200) {
    throw new Error(json.message || `业务错误: ${json.code}`)
  }
  return json.data
}

/**
 * 查询告警列表（分页）
 * 后端 GET /api/alert，返回 data 为分页对象 { records, total, current, size }
 */
export async function fetchAlerts(
  params: FetchAlertsParams,
): Promise<{ records: AlertItem[]; total: number }> {
  const qs = new URLSearchParams()
  if (params.status) qs.set('status', params.status)
  if (params.severity !== undefined) qs.set('severity', String(params.severity))
  if (params.page !== undefined) qs.set('page', String(params.page))
  if (params.size !== undefined) qs.set('size', String(params.size))
  const page = await request<PageResult<AlertItem>>(
    `${BASE_URL}/api/alert?${qs.toString()}`,
  )
  return { records: page.records, total: page.total }
}

/**
 * 查询告警统计（含总数、各状态数、各严重级别数、近24小时趋势）
 * 后端 GET /api/alert/stats
 *
 * 后端原始返回结构：
 *   { total, statusCount: {OPEN, ACK, RESOLVED},
 *     severityCount: { "7": 160, "8": 433, ... },
 *     hourlyTrend: { "2026-07-27 16:00": 479, ... },
 *     recent24hCount }
 * 前端 AlertStats 期望扁平字段 + trend 数组，这里做一次映射。
 */
export async function fetchAlertStats(): Promise<AlertStats> {
  const raw = await request<{
    total?: number
    statusCount?: Record<string, number>
    severityCount?: Record<string, number>
    hourlyTrend?: Record<string, number>
    recent24hCount?: number
  }>(`${BASE_URL}/api/alert/stats`)

  const sc = raw.statusCount ?? {}
  const sev = raw.severityCount ?? {}
  // 严重级别分桶：>=8 严重，5-7 高危，3-4 中危，1-2 低危
  let criticalCount = 0
  let highCount = 0
  let mediumCount = 0
  let lowCount = 0
  for (const [k, v] of Object.entries(sev)) {
    const level = Number(k)
    const count = Number(v) || 0
    if (level >= 8) criticalCount += count
    else if (level >= 5) highCount += count
    else if (level >= 3) mediumCount += count
    else lowCount += count
  }

  // hourlyTrend Map -> trend 数组（按时间升序，便于折线图渲染）
  const trend = Object.entries(raw.hourlyTrend ?? {})
    .map(([hour, count]) => ({ hour, count: Number(count) || 0 }))
    .sort((a, b) => a.hour.localeCompare(b.hour))

  return {
    total: Number(raw.total) || 0,
    openCount: Number(sc.OPEN) || 0,
    ackCount: Number(sc.ACK) || 0,
    resolvedCount: Number(sc.RESOLVED) || 0,
    criticalCount,
    highCount,
    mediumCount,
    lowCount,
    trend,
  }
}

/**
 * 确认告警（OPEN -> ACK）
 * 后端 PUT /api/alert/{id}/ack
 */
export async function ackAlert(id: number): Promise<void> {
  await request<unknown>(`${BASE_URL}/api/alert/${id}/ack`, { method: 'PUT' })
}

/**
 * 解决告警（-> RESOLVED）
 * 后端 PUT /api/alert/{id}/resolve
 */
export async function resolveAlert(id: number): Promise<void> {
  await request<unknown>(`${BASE_URL}/api/alert/${id}/resolve`, {
    method: 'PUT',
  })
}
