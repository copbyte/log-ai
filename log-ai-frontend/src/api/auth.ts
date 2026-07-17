import request from './request'
import type { ApiResult, LoginRequest, LoginResponse } from '../types'

export function login(data: LoginRequest) {
  return request.post<ApiResult<LoginResponse>>('/auth/login', data)
}