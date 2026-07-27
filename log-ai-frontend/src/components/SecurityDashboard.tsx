import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Empty,
  List,
  message,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ReloadOutlined, WarningOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  ackAlert,
  fetchAlerts,
  fetchAlertStats,
  resolveAlert,
} from '@/api/security'
import type { AlertItem, AlertStats } from '@/api/security'

const { Title } = Typography

/** 严重级别 -> Tag 颜色：>=8 红，5-7 橙，3-4 黄，1-2 蓝 */
function severityColor(severity: number): string {
  if (severity >= 8) return 'red'
  if (severity >= 5) return 'orange'
  if (severity >= 3) return 'gold'
  return 'blue'
}

function severityLabel(severity: number): string {
  if (severity >= 8) return '严重'
  if (severity >= 5) return '高危'
  if (severity >= 3) return '中危'
  return '低危'
}

/** 状态 -> Tag 颜色：OPEN 红，ACK 橙，RESOLVED 绿 */
function statusColor(status: AlertItem['status']): string {
  if (status === 'OPEN') return 'red'
  if (status === 'ACK') return 'orange'
  return 'green'
}

function statusLabel(status: AlertItem['status']): string {
  if (status === 'OPEN') return '待处理'
  if (status === 'ACK') return '处理中'
  return '已解决'
}

/** 安全态势感知大屏：统计卡片 + 告警列表 + 攻击源 IP 排行 + 趋势图 */
export default function SecurityDashboard() {
  const [alerts, setAlerts] = useState<AlertItem[]>([])
  const [total, setTotal] = useState(0)
  const [stats, setStats] = useState<AlertStats | null>(null)
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [resp, s] = await Promise.all([
        fetchAlerts({ page, size: pageSize }),
        fetchAlertStats(),
      ])
      setAlerts(resp.records)
      setTotal(resp.total)
      setStats(s)
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e)
      message.error(`加载告警数据失败：${err}`)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  // 首次及分页变化时加载
  useEffect(() => {
    load()
  }, [load])

  // 每 30 秒自动刷新
  useEffect(() => {
    const timer = setInterval(load, 30000)
    return () => clearInterval(timer)
  }, [load])

  // 攻击源 IP 排行：从当前告警列表聚合 srcIp 出现次数，Top 10
  const topSrcIps = useMemo(() => {
    const counter = new Map<string, number>()
    for (const a of alerts) {
      const ip = a.srcIp
      if (!ip) continue
      counter.set(ip, (counter.get(ip) ?? 0) + 1)
    }
    return Array.from(counter, ([ip, count]) => ({ ip, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10)
  }, [alerts])

  const handleAck = async (id: number) => {
    setActionLoading(id)
    try {
      await ackAlert(id)
      message.success('已确认告警')
      await load()
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e)
      message.error(`确认失败：${err}`)
    } finally {
      setActionLoading(null)
    }
  }

  const handleResolve = async (id: number) => {
    setActionLoading(id)
    try {
      await resolveAlert(id)
      message.success('已解决告警')
      await load()
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e)
      message.error(`解决失败：${err}`)
    } finally {
      setActionLoading(null)
    }
  }

  const columns: ColumnsType<AlertItem> = [
    {
      title: '严重级别',
      dataIndex: 'severity',
      width: 100,
      render: (v: number) => (
        <Tag color={severityColor(v)}>
          {severityLabel(v)} ({v})
        </Tag>
      ),
    },
    { title: '规则名称', dataIndex: 'ruleName', ellipsis: true },
    {
      title: '源 IP',
      dataIndex: 'srcIp',
      width: 140,
      render: (v?: string) => v || '-',
    },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: AlertItem['status']) => (
        <Tag color={statusColor(v)}>{statusLabel(v)}</Tag>
      ),
    },
    { title: '时间', dataIndex: 'createTime', width: 160 },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            disabled={record.status !== 'OPEN' || actionLoading === record.id}
            loading={actionLoading === record.id}
            onClick={() => handleAck(record.id)}
          >
            确认
          </Button>
          <Button
            size="small"
            type="link"
            disabled={record.status === 'RESOLVED' || actionLoading === record.id}
            loading={actionLoading === record.id}
            onClick={() => handleResolve(record.id)}
          >
            解决
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
      <Spin spinning={loading}>
        {/* 顶部标题 + 刷新按钮 */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
          }}
        >
          <Title level={5} style={{ margin: 0 }}>
            安全态势感知
          </Title>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            刷新
          </Button>
        </div>

        {/* 顶部统计卡片 */}
        <Row gutter={16}>
          <Col xs={12} md={6}>
            <Card>
              <Statistic
                title="告警总数"
                value={stats?.total ?? 0}
                prefix={<WarningOutlined />}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card>
              <Statistic
                title="待处理"
                value={stats?.openCount ?? 0}
                valueStyle={{ color: '#cf1322' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card>
              <Statistic
                title="高危"
                value={(stats?.criticalCount ?? 0) + (stats?.highCount ?? 0)}
                valueStyle={{ color: '#fa8c16' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card>
              <Statistic
                title="已解决"
                value={stats?.resolvedCount ?? 0}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 中间两栏：告警列表 + 攻击源 IP 排行 */}
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col xs={24} lg={16}>
            <Card title="告警列表" styles={{ body: { padding: 0 } }}>
              <Table<AlertItem>
                rowKey="id"
                columns={columns}
                dataSource={alerts}
                pagination={{
                  current: page,
                  pageSize,
                  total,
                  showSizeChanger: true,
                  onChange: (p, s) => {
                    setPage(p)
                    setPageSize(s)
                  },
                }}
                scroll={{ x: 900 }}
                size="small"
              />
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="攻击源 IP 排行 (Top 10)" styles={{ body: { padding: '12px 0' } }}>
              {topSrcIps.length === 0 ? (
                <Empty description="暂无数据" style={{ padding: 24 }} />
              ) : (
                <List
                  itemLayout="horizontal"
                  dataSource={topSrcIps}
                  renderItem={(item, index) => (
                    <List.Item style={{ padding: '8px 16px' }}>
                      <List.Item.Meta
                        avatar={
                          <Tag color={index < 3 ? 'red' : 'default'}>
                            #{index + 1}
                          </Tag>
                        }
                        title={item.ip}
                        description={`告警 ${item.count} 次`}
                      />
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Col>
        </Row>

        {/* 底部：最近 24 小时告警趋势 */}
        <Card title="最近 24 小时告警趋势" style={{ marginTop: 16 }}>
          {(stats?.trend?.length ?? 0) === 0 ? (
            <Empty description="暂无趋势数据" style={{ padding: 24 }} />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <AreaChart data={stats?.trend ?? []}>
                <defs>
                  <linearGradient id="alertTrend" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#1677ff" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="#1677ff" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(128,128,128,0.2)" />
                <XAxis
                  dataKey="hour"
                  tickFormatter={(v) => String(v).slice(-5)}
                  tick={{ fontSize: 11, fill: 'rgba(140,140,140,0.85)' }}
                />
                <YAxis
                  allowDecimals={false}
                  tick={{ fontSize: 11, fill: 'rgba(140,140,140,0.85)' }}
                />
                <RTooltip />
                <Area
                  type="monotone"
                  dataKey="count"
                  name="告警数"
                  stroke="#1677ff"
                  strokeWidth={2}
                  fill="url(#alertTrend)"
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Spin>
    </div>
  )
}
