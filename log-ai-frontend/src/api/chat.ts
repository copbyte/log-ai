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
 * 流式对话接口（SSE，支持多轮上下文）
 * 后端：mcp-server ChatController POST /api/chat/stream
 * 请求体：{ message: string, history: ChatHistoryItem[] }
 * 响应：text/event-stream，每个事件格式：
 *   event: delta\ndata: 文本片段\n\n
 * 流结束发送：event: done\ndata: [DONE]\n\n
 * 错误事件：event: error\ndata: 错误信息\n\n
 *
 * @param message 当前用户输入
 * @param onChunk 每收到一个文本片段的回调（增量）
 * @param signal  可选 AbortSignal，用于取消请求
 * @param history 历史对话消息（按时间升序，不包含当前 message）
 */
export async function streamMessage(
  message: string,
  onChunk: (delta: string) => void,
  signal?: AbortSignal,
  history: ChatHistoryItem[] = [],
): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, history }),
    signal,
  })
  if (!resp.ok) {
    throw new Error(`流式请求失败: ${resp.status} ${resp.statusText}`)
  }
  if (!resp.body) {
    throw new Error('响应体为空，浏览器不支持流式读取')
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  // SSE 事件之间用空行（\n\n）分隔，buffer 缓存未结束的事件
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // 按 \n\n 切分出完整事件，剩余不完整的留到下次
    let sepIndex: number
    while ((sepIndex = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, sepIndex)
      buffer = buffer.slice(sepIndex + 2)
      handleSseEvent(rawEvent, onChunk)
    }
  }
  // 处理 buffer 中残余的最后一个事件（无尾随 \n\n 的情况）
  if (buffer.trim()) {
    handleSseEvent(buffer, onChunk)
  }
}

/**
 * 解析单个 SSE 事件文本，根据 event 类型分发回调
 * 事件文本示例：
 *   event: delta\n
 *   data: 你好
 */
function handleSseEvent(rawEvent: string, onChunk: (delta: string) => void) {
  const lines = rawEvent.split('\n')
  let event = 'message'
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // data: 后面的内容（保留前导空格被 trim 掉一个，标准 SSE 协议规定 data: 后只能有 1 个空格）
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  if (dataLines.length === 0) return
  const data = dataLines.join('\n')

  if (event === 'done') {
    // [DONE] 哨兵，前端循环自然结束，无需特殊处理
    return
  }
  if (event === 'error') {
    throw new Error(data || 'AI 流式响应出错')
  }
  // 默认 delta 事件：把文本片段交给回调
  if (event === 'delta' || event === 'message') {
    onChunk(data)
  }
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
