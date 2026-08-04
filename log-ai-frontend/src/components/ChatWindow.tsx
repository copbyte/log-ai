import { useEffect, useRef, useState } from 'react'
import { Typography } from 'antd'
import type { Message } from '@/types'
import { sendMessage, emptyMessage } from '@/api/chat'
import MessageItem from './MessageItem'
import InputBox from './InputBox'
import QuickActions from './QuickActions'

const { Title, Text } = Typography

const WELCOME: Message = {
  id: 'welcome',
  role: 'assistant',
  content:
    '你好，我是日志分析助手。你可以问我：\n\n' +
    '- **搜索日志**：如"搜索最近 ERROR 级别日志"\n' +
    '- **链路追踪**：如"查询 traceId=trace-001 的链路日志"\n' +
    '- **异常聚类**：如"对最近的异常做聚类分析"\n' +
    '- **根因定位**：如"根据 traceId=trace-001 做根因分析"\n\n' +
    '也可以点击下方快捷按钮快速开始。',
  createdAt: Date.now(),
}

export default function ChatWindow() {
  const [messages, setMessages] = useState<Message[]>([WELCOME])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  // 消息列表变化时滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages])

  const handleSend = async () => {
    const text = input.trim()
    if (!text || loading) return

    const userMsg = emptyMessage('user', text)
    const aiMsg = emptyMessage('assistant')
    // 构造历史上下文：排除欢迎语、pending、error、空内容消息
    const history = messages
      .filter(
        (m) =>
          m.id !== 'welcome' &&
          !m.pending &&
          !m.error &&
          m.content &&
          m.content.trim(),
      )
      .map((m) => ({ role: m.role, content: m.content }))

    setMessages((prev) => [...prev, userMsg, aiMsg])
    setInput('')
    setLoading(true)

    try {
      const resp = await sendMessage(text, history)
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsg.id ? { ...m, content: resp, pending: false } : m,
        ),
      )
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e)
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsg.id
            ? { ...m, pending: false, error: `请求失败：${err}` }
            : m,
        ),
      )
    } finally {
      setLoading(false)
    }
  }

  const handleClear = () => {
    setMessages([WELCOME])
    setInput('')
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid var(--border-color, #f0f0f0)',
        }}
      >
        <Title level={5} style={{ margin: 0 }}>
          日志分析对话
        </Title>
        <Text type="secondary" style={{ fontSize: 12 }}>
          基于 MCP + Spring AI · 自然语言查询日志
        </Text>
      </div>

      <div
        ref={listRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '0 16px',
        }}
      >
        {messages.map((m) => (
          <MessageItem key={m.id} message={m} />
        ))}
      </div>

      <div style={{ padding: '8px 16px 0' }}>
        <QuickActions onPick={(p) => setInput(p)} disabled={loading} />
      </div>

      <InputBox
        value={input}
        onChange={setInput}
        onSend={handleSend}
        onClear={handleClear}
        loading={loading}
      />
    </div>
  )
}
