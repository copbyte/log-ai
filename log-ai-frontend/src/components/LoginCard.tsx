import { useState } from 'react'
import { Button, Card, Form, Input, Typography, message } from 'antd'
import { RobotOutlined } from '@ant-design/icons'
import { login, setToken, setUsername } from '@/api/auth'

interface Props {
  onSuccess: (username: string) => void
}

/** 登录卡片：登录成功后保存 JWT 并进入主界面 */
export default function LoginCard({ onSuccess }: Props) {
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const data = await login(values.username, values.password)
      setToken(data.token)
      setUsername(data.username)
      message.success('登录成功')
      onSuccess(data.username)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--content-bg, #f5f5f5)',
      }}
    >
      <Card style={{ width: 360 }}>
        <div style={{ textAlign: 'center', marginBottom: 16 }}>
          <RobotOutlined style={{ fontSize: 32, color: '#1677ff' }} />
          <Typography.Title level={4} style={{ marginTop: 8, marginBottom: 4 }}>
            Log Monitor AI
          </Typography.Title>
          <Typography.Text type="secondary">请登录后使用</Typography.Text>
        </div>
        <Form onFinish={onFinish} size="large">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
