import { useEffect, useRef, useState } from 'react'
import {
  Button,
  Drawer,
  Empty,
  List,
  Pagination,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import { HistoryOutlined, ReloadOutlined } from '@ant-design/icons'
import type { Message } from '@/types'
import { streamMessage, emptyMessage, fetchChatHistory } from '@/api/chat'
import type { ChatHistoryRecord } from '@/api/chat'
import { fetchAuditLogs } from '@/api/security'
import type { AuditLogItem } from '@/api/security'
import { getUsername } from '@/api/auth'
import MessageItem from './MessageItem'
import InputBox from './InputBox'
import QuickActions from './QuickActions'
import { useVoiceAssistant } from '@/hooks/useVoiceAssistant'

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
  // ===== 历史记录抽屉 =====
  const [historyOpen, setHistoryOpen] = useState(false)
  const [chatRecords, setChatRecords] = useState<ChatHistoryRecord[]>([])
  const [chatTotal, setChatTotal] = useState(0)
  const [chatPage, setChatPage] = useState(1)
  const [chatLoading, setChatLoading] = useState(false)
  const [queryRecords, setQueryRecords] = useState<AuditLogItem[]>([])
  const [queryLoading, setQueryLoading] = useState(false)

  const loadChatHistory = async (page: number) => {
    setChatLoading(true)
    try {
      const data = await fetchChatHistory(page, 20)
      setChatRecords(data.records)
      setChatTotal(data.total)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载分析记录失败')
    } finally {
      setChatLoading(false)
    }
  }

  const loadQueryHistory = async () => {
    setQueryLoading(true)
    try {
      // 只展示当前用户的日志查询/链路查询记录（审计按用户隔离）
      const data = await fetchAuditLogs({
        username: getUsername() ?? undefined,
        size: 100,
      })
      setQueryRecords(
        data.records.filter(
          (r) => r.operation === 'LOG_QUERY' || r.operation === 'LOG_TRACE_QUERY',
        ),
      )
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载查询记录失败')
    } finally {
      setQueryLoading(false)
    }
  }

  const openHistory = () => {
    setHistoryOpen(true)
    loadChatHistory(1)
    loadQueryHistory()
  }

  // 消息列表变化时滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages])

  // ===== 语音助手 =====
  // AI 识别完成后把文字填入输入框（用户可确认/修改后再发送）
  const { state: voiceState, start: voiceStart, stop: voiceStop, isSupported: voiceSupported } =
    useVoiceAssistant((text) => {
      setInput(text)
    })

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
      // 流式：每收到一个文本片段就追加到 aiMsg.content，并取消 pending 状态
      // —— 原同步实现（已注释保留）——
      // const resp = await sendMessage(text, history)
      // setMessages((prev) =>
      //   prev.map((m) =>
      //     m.id === aiMsg.id ? { ...m, content: resp, pending: false } : m,
      //   ),
      // )
      await streamMessage(
        text,
        (delta) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsg.id
                ? { ...m, content: m.content + delta, pending: false }
                : m,
            ),
          )
        },
        undefined,
        history,
      )
      // 流结束兜底：确保 pending 为 false（极端情况未收到任何 chunk）
      setMessages((prev) =>
        prev.map((m) =>
          m.id === aiMsg.id ? { ...m, pending: false } : m,
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
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div>
          <Title level={5} style={{ margin: 0 }}>
            日志分析对话
          </Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            基于 MCP + Spring AI · 自然语言查询日志
          </Text>
        </div>
        <Button icon={<HistoryOutlined />} onClick={openHistory}>
          历史记录
        </Button>
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
        voiceState={voiceState}
        voiceSupported={voiceSupported}
        onVoiceToggle={() => (voiceState === 'idle' ? voiceStart() : voiceStop())}
      />

      <Drawer
        title="历史记录"
        width={600}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              loadChatHistory(chatPage)
              loadQueryHistory()
            }}
          >
            刷新
          </Button>
        }
      >
        <Tabs
          items={[
            {
              key: 'chat',
              label: '分析记录',
              children: (
                <Spin spinning={chatLoading}>
                  {chatRecords.length === 0 ? (
                    <Empty description="暂无分析记录" style={{ padding: 32 }} />
                  ) : (
                    <>
                      <List
                        dataSource={chatRecords}
                        renderItem={(item) => (
                          <List.Item key={item.id}>
                            <div style={{ width: '100%' }}>
                              <div
                                style={{
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  marginBottom: 4,
                                }}
                              >
                                <Tag color={item.role === 'user' ? 'blue' : 'green'}>
                                  {item.role === 'user' ? '我' : 'AI'}
                                </Tag>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                  {item.createTime}
                                </Text>
                              </div>
                              <div
                                style={{
                                  whiteSpace: 'pre-wrap',
                                  wordBreak: 'break-word',
                                  fontSize: 13,
                                }}
                              >
                                {item.content}
                              </div>
                            </div>
                          </List.Item>
                        )}
                      />
                      <div style={{ textAlign: 'right', marginTop: 12 }}>
                        <Pagination
                          current={chatPage}
                          pageSize={20}
                          total={chatTotal}
                          showSizeChanger={false}
                          onChange={(p) => {
                            setChatPage(p)
                            loadChatHistory(p)
                          }}
                        />
                      </div>
                    </>
                  )}
                </Spin>
              ),
            },
            {
              key: 'query',
              label: '查询记录',
              children: (
                <Spin spinning={queryLoading}>
                  {queryRecords.length === 0 ? (
                    <Empty description="暂无查询记录" style={{ padding: 32 }} />
                  ) : (
                    <List
                      dataSource={queryRecords}
                      renderItem={(item) => (
                        <List.Item key={item.id}>
                          <div style={{ width: '100%' }}>
                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                marginBottom: 4,
                              }}
                            >
                              <Tag color="cyan">
                                {item.operation === 'LOG_QUERY'
                                  ? '日志查询'
                                  : '链路查询'}
                              </Tag>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                {item.createTime}
                              </Text>
                            </div>
                            <Typography.Paragraph
                              type="secondary"
                              style={{ marginBottom: 0, fontSize: 13 }}
                              ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
                            >
                              {item.params || '无参数'}
                            </Typography.Paragraph>
                          </div>
                        </List.Item>
                      )}
                    />
                  )}
                </Spin>
              ),
            },
          ]}
        />
      </Drawer>
    </div>
  )
}
