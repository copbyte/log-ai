import { Layout, Tabs, Typography } from 'antd'
import { RobotOutlined } from '@ant-design/icons'
import ChatWindow from '@/components/ChatWindow'
import SecurityDashboard from '@/components/SecurityDashboard'
import ThemeToggle from '@/components/ThemeToggle'
import type { ThemeMode } from '@/hooks/useTheme'

const { Header, Content } = Layout
const { Title } = Typography

interface Props {
  mode: ThemeMode
  onToggleTheme: () => void
}

/** 应用主体：顶部标题栏 + 内容区（AI 对话 / 安全态势 切换） */
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
        <Tabs
          defaultActiveKey="chat"
          size="large"
          className="app-tabs"
          tabBarStyle={{ margin: 0, padding: '0 16px' }}
          destroyInactiveTabPane={false}
          items={[
            { key: 'chat', label: 'AI 日志分析', children: <ChatWindow /> },
            { key: 'security', label: '安全态势', children: <SecurityDashboard /> },
          ]}
        />
        {/* 让 Tabs 内容区撑满高度：对话页内部滚动，态势大屏内部滚动 */}
        <style>{`
          .app-tabs { height: 100%; display: flex; flex-direction: column; }
          .app-tabs > .ant-tabs-nav { margin: 0; flex: 0 0 auto; }
          .app-tabs > .ant-tabs-content-holder { flex: 1 1 auto; min-height: 0; }
          .app-tabs > .ant-tabs-content-holder > .ant-tabs-content,
          .app-tabs > .ant-tabs-content-holder > .ant-tabs-content > .ant-tabs-tabpane { height: 100%; }
          .app-tabs > .ant-tabs-content-holder > .ant-tabs-content > .ant-tabs-tabpane { overflow: hidden; }
        `}</style>
      </Content>
    </Layout>
  )
}
