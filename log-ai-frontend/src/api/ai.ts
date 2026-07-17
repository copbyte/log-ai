import request from './request'
import type { ApiResult, PageResult, AiAnalysisResult } from '../types'

export function getAiResults(page: number = 1, size: number = 20) {
  return request.get<ApiResult<PageResult<AiAnalysisResult>>>('/ai/results', {
    params: { page, size },
  })
}

export function getAiResultById(id: number) {
  return request.get<ApiResult<AiAnalysisResult>>(`/ai/results/${id}`)
}

export function triggerAiAnalysis(logEntryId: number) {
  return request.post<ApiResult<AiAnalysisResult>>(`/ai/analyze/${logEntryId}`)
}