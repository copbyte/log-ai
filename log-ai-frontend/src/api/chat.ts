import type { Message, Role } from '@/types'

const CHAT_URL = '/api/chat'

/**
 * 发送对话消息（同步模式）
 * 后端：mcp-server ChatController POST /api/chat
 * 请求体：{ message: string }
 * 响应体：{ response: string }
 */
export async function sendMessage(message: string): Promise<string> {
  const resp = await fetch(CHAT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
  if (!resp.ok) {
    throw new Error(`对话请求失败: ${resp.status} ${resp.statusText}`)
  }
  const data = await resp.json()
  return (data?.response ?? '').toString()
}

/**
 * 占位：流式对话接口（后端暂未实现 SSE，预留接口签名）
 * 后续如开启流式，将后端 ChatController 改为返回 SseEmitter 或 Flux<ServerSentEvent>，
 * 这里改用 ReadableStream 解析 SSE 事件，逐步更新 Message.content。
 */
export async function streamMessage(
  message: string,
  onChunk: (delta: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  // 当前未启用，直接走同步
  const text = await sendMessage(message)
  onChunk(text)
}

/** 生成客户端消息 ID */
export function genMessageId(): string {
  return `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/** 构造一条空消息占位 */
export function emptyMessage(role: Role, content = ''): Message {
  return {
    id: genMessageId(),
    role,
    content,
    createdAt: Date.now(),
    pending: role === 'assistant',
  }
}
