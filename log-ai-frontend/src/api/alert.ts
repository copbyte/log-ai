import request from './request'
import type { ApiResult, PageResult, AlertRule, AlertRecord } from '../types'

export function getAlertRules(page: number = 1, size: number = 20) {
  return request.get<ApiResult<PageResult<AlertRule>>>('/alert/rules', {
    params: { page, size },
  })
}

export function getAlertRuleById(id: number) {
  return request.get<ApiResult<AlertRule>>(`/alert/rules/${id}`)
}

export function createAlertRule(rule: AlertRule) {
  return request.post<ApiResult<AlertRule>>('/alert/rules', rule)
}

export function updateAlertRule(id: number, rule: AlertRule) {
  return request.put<ApiResult<AlertRule>>(`/alert/rules/${id}`, rule)
}

export function deleteAlertRule(id: number) {
  return request.delete<ApiResult<void>>(`/alert/rules/${id}`)
}

export interface AlertRecordFilters {
  ruleId?: number
  notifyStatus?: string
  startTime?: string
  endTime?: string
}

export function getAlertRecords(page: number = 1, size: number = 20, filters?: AlertRecordFilters) {
  return request.get<ApiResult<PageResult<AlertRecord>>>('/alert/records', {
    params: { page, size, ...filters },
  })
}

export function getAlertRecordById(id: number) {
  return request.get<ApiResult<AlertRecord>>(`/alert/records/${id}`)
}
