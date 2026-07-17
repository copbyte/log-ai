import request from './request'
import type { ApiResult, PageResult, LogEntry } from '../types'

export interface LogEntryFilters {
  logLevel?: string
  className?: string
  fileName?: string
  threadName?: string
  startTime?: string
  endTime?: string
  keyword?: string
}

export function getLogEntries(page: number = 1, size: number = 20, filters?: LogEntryFilters) {
  return request.get<ApiResult<PageResult<LogEntry>>>('/log/entries', {
    params: { page, size, ...filters },
  })
}

export function getLogEntryById(id: number) {
  return request.get<ApiResult<LogEntry>>(`/log/entries/${id}`)
}
