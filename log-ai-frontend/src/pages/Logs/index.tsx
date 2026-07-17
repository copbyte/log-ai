import { useState, useEffect } from 'react'
import { Table, Card, Tag, Typography, Drawer, Descriptions, Form, Select, Input, DatePicker, Button, Space, message } from 'antd'
import { SearchOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { getLogEntries, type LogEntryFilters } from '../../api/log'
import { triggerAiAnalysis } from '../../api/ai'
import type { LogEntry } from '../../types'
import dayjs from 'dayjs'

const { Title } = Typography
const { RangePicker } = DatePicker

const levelColors: Record<string, string> = {
  ERROR: 'red',
  WARN: 'orange',
  INFO: 'blue',
  DEBUG: 'green',
  TRACE: 'default',
  FATAL: 'purple',
}

const levelOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

export default function Logs() {
  const [data, setData] = useState<LogEntry[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [selected, setSelected] = useState<LogEntry | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [filters, setFilters] = useState<LogEntryFilters>({})
  const [form] = Form.useForm()

  const buildFilters = (values: Record<string, unknown>): LogEntryFilters => {
    const f: LogEntryFilters = {}
    if (values.logLevel) f.logLevel = values.logLevel as string
    if (values.className) f.className = values.className as string
    if (values.fileName) f.fileName = values.fileName as string
    if (values.threadName) f.threadName = values.threadName as string
    if (values.keyword) f.keyword = values.keyword as string
    if (values.timeRange && Array.isArray(values.timeRange) && values.timeRange.length === 2) {
      f.startTime = (values.timeRange[0] as dayjs.Dayjs).toISOString()
      f.endTime = (values.timeRange[1] as dayjs.Dayjs).toISOString()
    }
    return f
  }

  const fetchData = async (p: number, s: number, f: LogEntryFilters) => {
    setLoading(true)
    try {
      const res = await getLogEntries(p, s, f)
      setData(res.data.data.records)
      setTotal(res.data.data.total)
    } catch {
      // handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData(page, pageSize, filters)
  }, [page, pageSize])

  const handleSearch = () => {
    const values = form.getFieldsValue()
    const f = buildFilters(values)
    setFilters(f)
    setPage(1)
    fetchData(1, pageSize, f)
  }

  const handleAiAnalyze = async () => {
    if (!selected) return
    setAnalyzing(true)
    try {
      await triggerAiAnalysis(selected.id)
      message.success('AI分析请求已发送，请前往 AI分析 页面查看结果')
    } catch {
      message.error('AI分析请求失败')
    } finally {
      setAnalyzing(false)
    }
  }
  const handleReset = () => {
    form.resetFields()
    setFilters({})
    setPage(1)
    fetchData(1, pageSize, {})
  }

  const columns: ColumnsType<LogEntry> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '级别',
      dataIndex: 'logLevel',
      key: 'logLevel',
      width: 80,
      render: (level: string) => (
        <Tag color={levelColors[level] || 'default'}>{level}</Tag>
      ),
    },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 180, ellipsis: true },
    { title: '类名', dataIndex: 'className', key: 'className', width: 200, ellipsis: true },
    {
      title: '日志内容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      render: (text: string) => (
        <span style={{ maxWidth: 400, display: 'inline-block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {text}
        </span>
      ),
    },
    {
      title: '日志时间',
      dataIndex: 'logTime',
      key: 'logTime',
      width: 180,
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    },
    { title: '线程', dataIndex: 'threadName', key: 'threadName', width: 120, ellipsis: true },
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
      <Title level={4} style={{ marginBottom: 16 }}>
        日志列表
      </Title>

      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" style={{ flexWrap: 'wrap', gap: 8 }}>
          <Form.Item name="logLevel" style={{ marginBottom: 8 }}>
            <Select placeholder="日志级别" allowClear style={{ width: 120 }}>
              {levelOptions.map((l) => (
                <Select.Option key={l} value={l}>{l}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="className" style={{ marginBottom: 8 }}>
            <Input placeholder="类名" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="fileName" style={{ marginBottom: 8 }}>
            <Input placeholder="文件名" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="threadName" style={{ marginBottom: 8 }}>
            <Input placeholder="线程名" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="timeRange" style={{ marginBottom: 8 }}>
            <RangePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: 360 }} />
          </Form.Item>
          <Form.Item name="keyword" style={{ marginBottom: 8 }}>
            <Input placeholder="搜索关键词" allowClear style={{ width: 200 }} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 1100 }}
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
        title="日志详情"
        open={!!selected}
        onClose={() => setSelected(null)}
        width={640}
      >
        {selected && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="ID">{selected.id}</Descriptions.Item>
            <Descriptions.Item label="日志级别">
              <Tag color={levelColors[selected.logLevel]}>{selected.logLevel}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="文件名">{selected.fileName}</Descriptions.Item>
            <Descriptions.Item label="类名">{selected.className}</Descriptions.Item>
            <Descriptions.Item label="线程">{selected.threadName}</Descriptions.Item>
            <Descriptions.Item label="日志时间">
              {dayjs(selected.logTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="入库时间">
              {dayjs(selected.createTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="日志内容">
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', maxHeight: 400, overflow: 'auto' }}>
                {selected.content}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
        {selected && (
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={analyzing}
              onClick={handleAiAnalyze}
            >
              AI 分析
            </Button>
          </div>
        )}
      </Drawer>
    </div>
  )
}
