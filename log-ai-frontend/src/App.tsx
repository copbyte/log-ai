import { Layout, Typography } from 'antd'
import { RobotOutlined } from '@ant-design/icons'
import ChatWindow from '@/components/ChatWindow'
import ThemeToggle from '@/components/ThemeToggle'
import type { ThemeMode } from '@/hooks/useTheme'

const { Header, Content } = Layout
const { Title } = Typography

interface Props {
  mode: ThemeMode
  onToggleTheme: () => void
}

/** 应用主体：顶部标题栏 + 对话主区 */
export default function App({ mode, onToggleTheme }: Props) {
  return (
    <Layout style={{ height: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 20px',
          background: 'var(--header-bg, #001529)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <RobotOutlined style={{ fontSize: 22, color: '#1677ff' }} />
          <Title level={4} style={{ color: 'var(--header-fg, #fff)', margin: 0 }}>
            Log Monitor AI
          </Title>
        </div>
        <ThemeToggle mode={mode} onToggle={onToggleTheme} />
      </Header>
      <Content style={{ background: 'var(--content-bg, #f5f5f5)' }}>
        <ChatWindow />
      </Content>
    </Layout>
  )
}
