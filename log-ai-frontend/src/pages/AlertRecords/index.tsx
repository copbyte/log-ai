import { useState, useEffect } from 'react'
import { Table, Card, Tag, Typography, Drawer, Descriptions, Form, Select, Input, DatePicker, Button, Space } from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { getAlertRecords, type AlertRecordFilters } from '../../api/alert'
import type { AlertRecord } from '../../types'
import dayjs from 'dayjs'

const { Title } = Typography
const { RangePicker } = DatePicker

const notifyStatusColors: Record<string, string> = {
  PENDING: 'orange',
  SUCCESS: 'green',
  FAIL: 'red',
}

const notifyStatusOptions = ['PENDING', 'SUCCESS', 'FAIL']

export default function AlertRecords() {
  const [data, setData] = useState<AlertRecord[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [selected, setSelected] = useState<AlertRecord | null>(null)
  const [filters, setFilters] = useState<AlertRecordFilters>({})
  const [form] = Form.useForm()

  const buildFilters = (values: Record<string, unknown>): AlertRecordFilters => {
    const f: AlertRecordFilters = {}
    if (values.ruleId !== undefined && values.ruleId !== '' && values.ruleId !== null) {
      f.ruleId = Number(values.ruleId)
    }
    if (values.notifyStatus) f.notifyStatus = values.notifyStatus as string
    if (values.timeRange && Array.isArray(values.timeRange) && values.timeRange.length === 2) {
      f.startTime = (values.timeRange[0] as dayjs.Dayjs).toISOString()
      f.endTime = (values.timeRange[1] as dayjs.Dayjs).toISOString()
    }
    return f
  }

  const fetchData = async (p: number, s: number, f: AlertRecordFilters) => {
    setLoading(true)
    try {
      const res = await getAlertRecords(p, s, f)
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

  const handleReset = () => {
    form.resetFields()
    setFilters({})
    setPage(1)
    fetchData(1, pageSize, {})
  }

  const columns: ColumnsType<AlertRecord> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '规则ID', dataIndex: 'ruleId', key: 'ruleId', width: 100 },
    { title: '日志ID', dataIndex: 'logEntryId', key: 'logEntryId', width: 100 },
    {
      title: '告警内容',
      dataIndex: 'alertContent',
      key: 'alertContent',
      width: 400,
      ellipsis: true,
    },
    {
      title: '通知状态',
      dataIndex: 'notifyStatus',
      key: 'notifyStatus',
      width: 100,
      render: (status: string) => (
        <Tag color={notifyStatusColors[status] || 'default'}>{status}</Tag>
      ),
    },
    {
      title: '创建时间',
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
      <Title level={4} style={{ marginBottom: 16 }}>
        告警记录
      </Title>

      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" style={{ flexWrap: 'wrap', gap: 8 }}>
          <Form.Item name="ruleId" style={{ marginBottom: 8 }}>
            <Input placeholder="规则ID" allowClear type="number" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="notifyStatus" style={{ marginBottom: 8 }}>
            <Select placeholder="通知状态" allowClear style={{ width: 140 }}>
              {notifyStatusOptions.map((s) => (
                <Select.Option key={s} value={s}>{s}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" style={{ marginBottom: 8 }}>
            <RangePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: 360 }} />
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
          scroll={{ x: 1000 }}
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
        title="告警记录详情"
        open={!!selected}
        onClose={() => setSelected(null)}
        width={640}
      >
        {selected && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="告警ID">{selected.id}</Descriptions.Item>
            <Descriptions.Item label="规则ID">{selected.ruleId}</Descriptions.Item>
            <Descriptions.Item label="关联日志ID">{selected.logEntryId}</Descriptions.Item>
            <Descriptions.Item label="通知状态">
              <Tag color={notifyStatusColors[selected.notifyStatus]}>
                {selected.notifyStatus}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="告警时间">
              {dayjs(selected.createTime).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="告警内容">
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', maxHeight: 400, overflow: 'auto' }}>
                {selected.alertContent}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}
