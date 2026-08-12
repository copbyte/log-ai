import { Button, Tooltip } from 'antd'
import { MoonOutlined, SunOutlined } from '@ant-design/icons'
import type { ThemeMode } from '@/hooks/useTheme'

interface Props {
  mode: ThemeMode
  onToggle: () => void
}

/** 主题切换按钮（暗色/浅色） */
export default function ThemeToggle({ mode, onToggle }: Props) {
  const isDark = mode === 'dark'
  return (
    <Tooltip title={isDark ? '切换到浅色' : '切换到暗色'}>
      <Button
        type="text"
        icon={isDark ? <SunOutlined /> : <MoonOutlined />}
        onClick={onToggle}
        style={{ color: 'var(--header-fg, #fff)' }}
      />
    </Tooltip>
  )
}
