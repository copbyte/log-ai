import { Timeline, Typography, Tag, Empty } from 'antd'
import type { TraceLogItem } from '@/types'

const { Text, Paragraph } = Typography

interface Props {
  traceId: string
  logs: TraceLogItem[]
}

const LEVEL_COLOR: Record<string, string> = {
  ERROR: 'red',
  WARN: 'orange',
  INFO: 'blue',
  DEBUG: 'default',
  TRACE: 'default',
  FATAL: 'red',
}

/** TraceID 链路时间线可视化 */
export default function TraceTimeline({ traceId, logs }: Props) {
  if (!logs.length) {
    return <Empty description={`未找到 TraceID=${traceId} 的链路日志`} />
  }

  const items = logs.map((log) => ({
    color: LEVEL_COLOR[log.logLevel?.toUpperCase()] ?? 'gray',
    children: (
      <div style={{ lineHeight: 1.6 }}>
        <div>
          <Tag color={LEVEL_COLOR[log.logLevel?.toUpperCase()] ?? 'default'}>
            {log.logLevel}
          </Tag>
          {log.serviceName && <Text type="secondary">{log.serviceName}</Text>}
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {log.logTime}
          </Text>
        </div>
        {log.className && (
          <Text code style={{ fontSize: 12 }}>
            {log.className}
          </Text>
        )}
        <Paragraph style={{ marginTop: 4, marginBottom: 0 }}>
          {log.content}
        </Paragraph>
      </div>
    ),
  }))

  return (
    <div>
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        链路时间线 · {traceId}
        <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal', marginLeft: 8 }}>
          共 {logs.length} 条
        </Text>
      </Typography.Title>
      <Timeline items={items} />
    </div>
  )
}
