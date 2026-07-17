import { useState, useEffect } from 'react'
import {
  Table, Card, Tag, Typography, Button, Modal, Form,
  Input, Select, Switch, Space, Popconfirm, message,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  getAlertRules, createAlertRule,
  updateAlertRule, deleteAlertRule,
} from '../../api/alert'
import type { AlertRule } from '../../types'

const { Title } = Typography
const { Option } = Select

const matchTypeLabels: Record<string, string> = { CONTAINS: '包含匹配', REGEX: '正则匹配' }
const notifyTypeLabels: Record<string, string> = { EMAIL: '邮件', DINGTALK: '钉钉', ALL: '全部' }

export default function AlertRules() {
  const [data, setData] = useState<AlertRule[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()

  const fetchData = async (p: number, s: number) => {
    setLoading(true)
    try {
      const res = await getAlertRules(p, s)
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

  const handleAdd = () => {
    setEditingRule(null)
    form.resetFields()
    form.setFieldsValue({ matchType: 'CONTAINS', notifyType: 'EMAIL', isEnabled: true })
    setModalOpen(true)
  }

  const handleEdit = (record: AlertRule) => {
    setEditingRule(record)
    form.setFieldsValue(record)
    setModalOpen(true)
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteAlertRule(id)
      message.success('删除成功')
      fetchData(page, pageSize)
    } catch {
      message.error('删除失败')
    }
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      if (editingRule?.id) {
        await updateAlertRule(editingRule.id, values)
        message.success('更新成功')
      } else {
        await createAlertRule(values)
        message.success('创建成功')
      }
      setModalOpen(false)
      fetchData(page, pageSize)
    } catch {
      // form validation error or API error
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<AlertRule> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 180 },
    {
      title: '关键词',
      dataIndex: 'keyword',
      key: 'keyword',
      width: 200,
      ellipsis: true,
    },
    {
      title: '日志级别',
      dataIndex: 'logLevel',
      key: 'logLevel',
      width: 100,
      render: (level: string | null) =>
        level ? <Tag color="blue">{level}</Tag> : <Tag>全部</Tag>,
    },
    {
      title: '匹配方式',
      dataIndex: 'matchType',
      key: 'matchType',
      width: 100,
      render: (type: string) => matchTypeLabels[type] || type,
    },
    {
      title: '通知方式',
      dataIndex: 'notifyType',
      key: 'notifyType',
      width: 100,
      render: (type: string) => <Tag>{notifyTypeLabels[type] || type}</Tag>,
    },
    {
      title: '启用',
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      width: 70,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'red'}>{enabled ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除此规则？"
            onConfirm={() => record.id && handleDelete(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={4} style={{ marginBottom: 0 }}>
          告警规则
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建规则
        </Button>
      </div>
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

      <Modal
        title={editingRule ? '编辑告警规则' : '新建告警规则'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="如：ERROR级别全捕获" />
          </Form.Item>
          <Form.Item
            name="keyword"
            label="关键词/正则"
            rules={[{ required: true, message: '请输入关键词或正则表达式' }]}
          >
            <Input placeholder="如：OutOfMemoryError" />
          </Form.Item>
          <Form.Item name="logLevel" label="日志级别">
            <Select allowClear placeholder="留空表示所有级别">
              {['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'].map((l) => (
                <Option key={l} value={l}>{l}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="matchType"
            label="匹配方式"
            rules={[{ required: true }]}
          >
            <Select>
              <Option value="CONTAINS">包含匹配</Option>
              <Option value="REGEX">正则匹配</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="notifyType"
            label="通知方式"
            rules={[{ required: true }]}
          >
            <Select>
              <Option value="EMAIL">邮件</Option>
              <Option value="DINGTALK">钉钉</Option>
              <Option value="ALL">全部</Option>
            </Select>
          </Form.Item>
          <Form.Item name="isEnabled" label="启用状态" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}