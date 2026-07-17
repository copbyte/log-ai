import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell } from 'recharts'
import { Typography, Empty, List, Tag } from 'antd'
import type { ClusterItem } from '@/types'

const { Text, Title } = Typography

interface Props {
  clusters: ClusterItem[]
}

const PALETTE = ['#1677ff', '#13c2c2', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#eb2f96']

/** 异常聚类结果图表（柱状图 + 详情列表） */
export default function ClusterChart({ clusters }: Props) {
  if (!clusters.length) {
    return <Empty description="未找到异常日志" />
  }

  // 截断过长的 key，避免横向柱状图标签溢出
  const chartData = clusters.slice(0, 10).map((c) => ({
    name: c.key.length > 40 ? c.key.slice(0, 40) + '…' : c.key,
    count: c.count,
    raw: c.key,
  }))

  return (
    <div>
      <Title level={5} style={{ marginTop: 0 }}>
        异常聚类结果
        <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal', marginLeft: 8 }}>
          共 {clusters.length} 类异常
        </Text>
      </Title>

      <div style={{ width: '100%', height: Math.max(200, chartData.length * 36) }}>
        <ResponsiveContainer>
          <BarChart data={chartData} layout="vertical" margin={{ left: 8, right: 16 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis type="number" allowDecimals={false} />
            <YAxis type="category" dataKey="name" width={300} tick={{ fontSize: 11 }} />
            <Tooltip
              formatter={(value: number) => [`${value} 次`, '出现次数']}
              labelFormatter={(_, payload) => payload?.[0]?.payload?.raw ?? ''}
            />
            <Bar dataKey="count" radius={[0, 4, 4, 0]}>
              {chartData.map((_, i) => (
                <Cell key={i} fill={PALETTE[i % PALETTE.length]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <List
        size="small"
        bordered
        style={{ marginTop: 12 }}
        dataSource={clusters}
        renderItem={(c, i) => (
          <List.Item>
            <div style={{ width: '100%' }}>
              <Tag color={PALETTE[i % PALETTE.length]}>
                {c.count} 次
              </Tag>
              {c.serviceName && <Text type="secondary" style={{ marginRight: 8 }}>{c.serviceName}</Text>}
              <Text style={{ fontSize: 12, fontFamily: 'monospace' }}>{c.key}</Text>
              {c.sample && (
                <div style={{ marginTop: 4, paddingLeft: 8, fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>
                  示例：{c.sample.length > 100 ? c.sample.slice(0, 100) + '…' : c.sample}
                </div>
              )}
            </div>
          </List.Item>
        )}
      />
    </div>
  )
}
