import { Typography, Spin, Alert } from 'antd'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Message, TraceLogItem, ClusterItem } from '@/types'
import TraceTimeline from './TraceTimeline'
import ClusterChart from './ClusterChart'

const { Text } = Typography

interface Props {
  message: Message
}

// 工具结构化数据块协议：
// 后端在工具结果中用 ```tool:kind\n{json}\n``` 嵌入结构化数据
// kind 取值：trace | cluster | rootCause | search
const TOOL_BLOCK_RE = /```tool:(\w+)\n([\s\S]*?)\n```/g

type Segment =
  | { type: 'text'; content: string }
  | { type: 'tool'; kind: string; data: unknown; raw: string }

function splitSegments(content: string): Segment[] {
  const segments: Segment[] = []
  let last = 0
  let m: RegExpExecArray | null
  TOOL_BLOCK_RE.lastIndex = 0
  while ((m = TOOL_BLOCK_RE.exec(content)) !== null) {
    if (m.index > last) {
      segments.push({ type: 'text', content: content.slice(last, m.index) })
    }
    const kind = m[1]
    const raw = m[2]
    let data: unknown = null
    try {
      data = JSON.parse(raw)
    } catch {
      data = null
    }
    segments.push({ type: 'tool', kind, data, raw })
    last = m.index + m[0].length
  }
  if (last < content.length) {
    segments.push({ type: 'text', content: content.slice(last) })
  }
  return segments
}

function renderTool(kind: string, data: unknown): React.ReactNode {
  if (!data) return null
  try {
    if (kind === 'trace' || kind === 'rootCause') {
      const d = data as { traceId?: string; logs?: TraceLogItem[] }
      return (
        <TraceTimeline
          traceId={d.traceId ?? ''}
          logs={Array.isArray(d.logs) ? d.logs : []}
        />
      )
    }
    if (kind === 'cluster') {
      const d = data as { clusters?: ClusterItem[] }
      return <ClusterChart clusters={Array.isArray(d.clusters) ? d.clusters : []} />
    }
    // search 结果暂用文本展示，由 Markdown 段落渲染
    return null
  } catch {
    return null
  }
}

/** 单条消息渲染：用户气泡 + 助手 Markdown + 工具结果可视化 */
export default function MessageItem({ message }: Props) {
  const isUser = message.role === 'user'

  if (isUser) {
    return (
      <div style={{ display: 'flex', justifyContent: 'flex-end', margin: '12px 0' }}>
        <div
          style={{
            maxWidth: '70%',
            padding: '10px 14px',
            borderRadius: 12,
            background: 'var(--bubble-user-bg, #1677ff)',
            color: 'var(--bubble-user-fg, #fff)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {message.content}
        </div>
      </div>
    )
  }

  // assistant
  const segments = splitSegments(message.content)

  return (
    <div style={{ display: 'flex', margin: '12px 0' }}>
      <div
        style={{
          maxWidth: '85%',
          padding: '12px 16px',
          borderRadius: 12,
          background: 'var(--bubble-assistant-bg, rgba(0,0,0,0.04))',
          color: 'var(--bubble-assistant-fg, inherit)',
          flex: 1,
        }}
      >
        {message.error ? (
          <Alert type="error" message={message.error} showIcon />
        ) : segments.length === 0 && message.pending ? (
          <Spin size="small" />
        ) : (
          segments.map((seg, i) =>
            seg.type === 'text' ? (
              seg.content.trim() ? (
                <div key={i} className="markdown-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{seg.content}</ReactMarkdown>
                </div>
              ) : null
            ) : (
              <div key={i} style={{ margin: '8px 0' }}>
                {renderTool(seg.kind, seg.data) ?? (
                  <Text type="secondary" code>
                    {seg.raw}
                  </Text>
                )}
              </div>
            ),
          )
        )}
        {message.pending && segments.length > 0 && (
          <div style={{ marginTop: 8 }}>
            <Spin size="small" />
          </div>
        )}
      </div>
    </div>
  )
}
