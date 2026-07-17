import { useState, useEffect } from 'react'
import { Table, Card, Tag, Typography, Button, Drawer, Descriptions, message } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { getAiResults, triggerAiAnalysis } from '../../api/ai'
import type { AiAnalysisResult } from '../../types'
import dayjs from 'dayjs'

const { Title } = Typography

export default function AIAnalysis() {
  const [data, setData] = useState<AiAnalysisResult[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [selected, setSelected] = useState<AiAnalysisResult | null>(null)
  const [analyzing, setAnalyzing] = useState(false)

  const fetchData = async (p: number, s: number) => {
    setLoading(true)
    try {
      const res = await getAiResults(p, s)
      setData(res.data.data.records)
      setTotal(res.data.data.total)
    } catch {
      // handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData(page, pageSize)
  }, [page, pageSize])

  const handleAnalyze = async (logEntryId: number) => {
    setAnalyzing(true)
    try {
      await triggerAiAnalysis(logEntryId)
      message.success('AI分析请求已发送')
      fetchData(page, pageSize)
    } catch {
      message.error('AI分析请求失败')
    } finally {
      setAnalyzing(false)
    }
  }

  const columns: ColumnsType<AiAnalysisResult> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '日志ID', dataIndex: 'logEntryId', key: 'logEntryId', width: 100 },
    {
      title: '摘要',
      dataIndex: 'summary',
      key: 'summary',
      width: 300,
      ellipsis: true,
    },
    {
      title: '根因',
      dataIndex: 'rootCause',
      key: 'rootCause',
      width: 400,
      ellipsis: true,
    },
    {
      title: '模型',
      dataIndex: 'modelName',
      key: 'modelName',
      width: 120,
      render: (name: string) => <Tag color="purple">{name}</Tag>,
    },
    {
      title: 'Token',
      dataIndex: 'tokensUsed',
      key: 'tokensUsed',
      width: 80,
    },
    {
      title: '分析时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right',
      render: (_, record) => (
        <a onClick={() => setSelected(record)}>详情</a>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ marginBottom: 0 }}>
          AI分析结果
        </Title>
      </div>
      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 1300 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p)
              setPageSize(s)
            },
          }}
        />
      </Card>

      <Drawer
        title="AI分析详情"
        open={!!selected}
        onClose={() => setSelected(null)}
        width={640}
      >
        {selected && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="分析ID">{selected.id}</Descriptions.Item>
            <Descriptions.Item label="关联日志ID">{selected.logEntryId}</Descriptions.Item>
            <Descriptions.Item label="模型">
              <Tag color="purple">{selected.modelName}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Token消耗">{selected.tokensUsed}</Descriptions.Item>
            <Descriptions.Item label="分析时间">
              {dayjs(selected.createTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="摘要">
              {selected.summary}
            </Descriptions.Item>
            <Descriptions.Item label="根因分析">
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {selected.rootCause}
              </pre>
            </Descriptions.Item>
            <Descriptions.Item label="修复建议">
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {selected.suggestion}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}