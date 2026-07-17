import { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Typography } from 'antd'
import {
  FileTextOutlined,
  RobotOutlined,
  AlertOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { getLogEntries } from '../../api/log'
import { getAiResults } from '../../api/ai'
import { getAlertRecords, getAlertRules } from '../../api/alert'
import dayjs from 'dayjs'

const { Title } = Typography

interface Stats {
  totalLogs: number
  totalAnalysis: number
  totalAlerts: number
  totalRules: number
}

export default function Dashboard() {
  const [stats, setStats] = useState<Stats>({
    totalLogs: 0,
    totalAnalysis: 0,
    totalAlerts: 0,
    totalRules: 0,
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStats = async () => {
      const results = await Promise.allSettled([
        getLogEntries(1, 1),
        getAiResults(1, 1),
        getAlertRecords(1, 1),
        getAlertRules(1, 1),
      ])

      const getTotal = (result: PromiseSettledResult<unknown>, label: string) => {
        if (result.status === 'fulfilled') {
          return (result.value as { data: { data: { total: number } } }).data.data.total
        }
        console.warn(`Dashboard: ${label} 服务不可用`)
        return 0
      }

      setStats({
        totalLogs: getTotal(results[0], '日志'),
        totalAnalysis: getTotal(results[1], 'AI分析'),
        totalAlerts: getTotal(results[2], '告警记录'),
        totalRules: getTotal(results[3], '告警规则'),
      })
      setLoading(false)
    }
    fetchStats()
  }, [])

  const cards = [
    {
      title: '日志总数',
      value: stats.totalLogs,
      icon: <FileTextOutlined />,
      color: '#1677ff',
    },
    {
      title: 'AI分析',
      value: stats.totalAnalysis,
      icon: <RobotOutlined />,
      color: '#722ed1',
    },
    {
      title: '告警规则',
      value: stats.totalRules,
      icon: <AlertOutlined />,
      color: '#fa8c16',
    },
    {
      title: '告警记录',
      value: stats.totalAlerts,
      icon: <WarningOutlined />,
      color: '#f5222d',
    },
  ]

  return (
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>
        仪表盘
      </Title>
      <Row gutter={[16, 16]}>
        {cards.map((card) => (
          <Col xs={24} sm={12} lg={6} key={card.title}>
            <Card loading={loading}>
              <Statistic
                title={card.title}
                value={card.value}
                prefix={
                  <span style={{ color: card.color, marginRight: 8 }}>
                    {card.icon}
                  </span>
                }
              />
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 24 }}>
        <Title level={5}>系统信息</Title>
        <Row gutter={[16, 16]}>
          <Col span={12}>
            <p>
              <strong>API网关地址：</strong>
              <code>http://localhost:8080</code>
            </p>
            <p>
              <strong>WebSocket日志：</strong>
              <code>ws://localhost:8080/ws/log</code>
            </p>
          </Col>
          <Col span={12}>
            <p>
              <strong>WebSocket告警：</strong>
              <code>ws://localhost:8080/ws/alert</code>
            </p>
            <p>
              <strong>当前时间：</strong>
              {dayjs().format('YYYY-MM-DD HH:mm:ss')}
            </p>
          </Col>
        </Row>
      </Card>
    </div>
  )
}