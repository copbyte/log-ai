import type { Message, Role } from '@/types'

const CHAT_URL = '/api/chat'

/**
 * 历史消息条目（与后端 ChatController.ChatMessage 对应）
 */
export interface ChatHistoryItem {
  role: Role
  content: string
}

/**
 * 发送对话消息（同步模式，支持多轮上下文）
 * 后端：mcp-server ChatController POST /api/chat
 * 请求体：{ message: string, history: ChatHistoryItem[] }
 * 响应体：{ response: string }
 *
 * @param message 当前用户输入
 * @param history 历史对话消息（按时间升序，不包含当前 message），后端会拼接为上下文
 */
export async function sendMessage(
  message: string,
  history: ChatHistoryItem[] = [],
): Promise<string> {
  const resp = await fetch(CHAT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, history }),
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
  history: ChatHistoryItem[] = [],
): Promise<void> {
  // 当前未启用，直接走同步
  const text = await sendMessage(message, history)
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
