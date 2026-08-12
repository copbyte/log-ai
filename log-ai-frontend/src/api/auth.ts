// 登录认证相关接口（与后端 AuthController 对应，端口 8081）

const BASE_URL = 'http://localhost:8081'
export const TOKEN_KEY = 'logai_token'
export const USERNAME_KEY = 'logai_username'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY)
}

export function setUsername(username: string): void {
  localStorage.setItem(USERNAME_KEY, username)
}

export function clearUsername(): void {
  localStorage.removeItem(USERNAME_KEY)
}

export interface LoginResponse {
  token: string
  expiresInSeconds: number
  username: string
}

/** 登录，成功后返回 JWT */
export async function login(username: string, password: string): Promise<LoginResponse> {
  const resp = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const json = await resp.json()
  if (!resp.ok || (json.code !== 0 && json.code !== 200)) {
    throw new Error(json.message || `登录失败: ${resp.status}`)
  }
  return json.data as LoginResponse
}
