import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { fetchAuditLogs } from '@/api/security'
import type { AuditLogItem } from '@/api/security'

const { Title } = Typography

/** 操作类型 -> Tag 颜色 */
function operationColor(operation: string): string {
  if (operation === 'LOGIN') return 'blue'
  if (operation === 'LOG_QUERY') return 'cyan'
  if (operation === 'ALERT_ACK') return 'orange'
  if (operation === 'RULE_CREATE') return 'purple'
  if (operation === 'AI_CHAT') return 'green'
  return 'default'
}

/** 操作类型 -> 中文名 */
function operationLabel(operation: string): string {
  const labels: Record<string, string> = {
    LOGIN: '登录',
    LOG_QUERY: '日志查询',
    ALERT_ACK: '告警处理',
    RULE_CREATE: '规则创建',
    AI_CHAT: 'AI 对话',
  }
  return labels[operation] ?? operation
}

const OPERATION_OPTIONS = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOG_QUERY', label: '日志查询' },
  { value: 'ALERT_ACK', label: '告警处理' },
  { value: 'RULE_CREATE', label: '规则创建' },
  { value: 'AI_CHAT', label: 'AI 对话' },
]

/** 审计日志页：展示关键操作记录，支持按操作类型/用户筛选 */
export default function AuditLogPanel() {
  const [records, setRecords] = useState<AuditLogItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  // 筛选条件：输入后点“查询”才生效
  const [operation, setOperation] = useState<string | undefined>()
  const [username, setUsername] = useState('')
  const [appliedOperation, setAppliedOperation] = useState<string | undefined>()
  const [appliedUsername, setAppliedUsername] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const resp = await fetchAuditLogs({
        operation: appliedOperation,
        username: appliedUsername || undefined,
        page,
        size: pageSize,
      })
      setRecords(resp.records)
      setTotal(resp.total)
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e)
      message.error(`加载审计日志失败：${err}`)
    } finally {
      setLoading(false)
    }
  }, [appliedOperation, appliedUsername, page, pageSize])

  useEffect(() => {
    load()
  }, [load])

  // 每 30 秒自动刷新，让新产生的审计记录及时可见
  useEffect(() => {
    const timer = setInterval(load, 30000)
    return () => clearInterval(timer)
  }, [load])

  const applyFilter = () => {
    setPage(1)
    setAppliedOperation(operation)
    setAppliedUsername(username.trim())
  }

  const resetFilter = () => {
    setOperation(undefined)
    setUsername('')
    setAppliedOperation(undefined)
    setAppliedUsername('')
    setPage(1)
  }

  const columns: ColumnsType<AuditLogItem> = [
    {
      title: '操作类型',
      dataIndex: 'operation',
      width: 140,
      render: (v: string) => (
        <Tag color={operationColor(v)}>{operationLabel(v)}</Tag>
      ),
    },
    { title: '用户', dataIndex: 'username', width: 120, render: (v?: string) => v || 'system' },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: AuditLogItem['result']) =>
        v === 'SUCCESS' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
    { title: '参数摘要', dataIndex: 'params', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '来源 IP', dataIndex: 'ip', width: 140, render: (v?: string) => v || '-' },
    {
      title: '失败原因',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    { title: '操作时间', dataIndex: 'createTime', width: 170 },
  ]

  return (
    <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
      <Spin spinning={loading}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
          }}
        >
          <Title level={5} style={{ margin: 0 }}>
            安全审计日志
          </Title>
          <Space>
            <Select
              placeholder="操作类型"
              allowClear
              style={{ width: 140 }}
              value={operation}
              options={OPERATION_OPTIONS}
              onChange={setOperation}
            />
            <Input
              placeholder="用户名"
              allowClear
              style={{ width: 160 }}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onPressEnter={applyFilter}
            />
            <Button type="primary" onClick={applyFilter}>
              查询
            </Button>
            <Button onClick={resetFilter}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              刷新
            </Button>
          </Space>
        </div>

        <Card styles={{ body: { padding: 0 } }}>
          <Table<AuditLogItem>
            rowKey="id"
            columns={columns}
            dataSource={records}
            expandable={{
              expandedRowRender: (record) => (
                <div style={{ padding: '8px 16px' }}>
                  <Typography.Text strong>参数摘要：</Typography.Text>
                  <div
                    style={{
                      marginTop: 4,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    {record.params || '-'}
                  </div>
                  {record.errorMessage ? (
                    <>
                      <Typography.Text strong>失败原因：</Typography.Text>
                      <div
                        style={{
                          marginTop: 4,
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        }}
                      >
                        {record.errorMessage}
                      </div>
                    </>
                  ) : null}
                </div>
              ),
            }}
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
            scroll={{ x: 1000 }}
            size="small"
          />
        </Card>
      </Spin>
    </div>
  )
}
