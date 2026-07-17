import { Button, Space, Tooltip } from 'antd'
import {
  BugOutlined,
  ClusterOutlined,
  NodeIndexOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import type { QuickAction } from '@/types'

const ACTIONS: QuickAction[] = [
  {
    key: 'search-error',
    label: '查 ERROR 日志',
    prompt: '帮我搜索最近的 ERROR 级别日志，看看有什么异常',
    icon: 'search',
  },
  {
    key: 'cluster',
    label: '异常聚类',
    prompt: '对最近的异常日志做聚类分析，按异常类型分组统计',
    icon: 'cluster',
  },
  {
    key: 'trace',
    label: '按 TraceID 查链路',
    prompt: '查询 traceId=trace-001 的链路日志',
    icon: 'node',
  },
  {
    key: 'root-cause',
    label: '根因定位',
    prompt: '根据 traceId=trace-001 做根因定位分析',
    icon: 'bug',
  },
]

const ICONS: Record<string, React.ReactNode> = {
  search: <SearchOutlined />,
  cluster: <ClusterOutlined />,
  node: <NodeIndexOutlined />,
  bug: <BugOutlined />,
}

interface Props {
  onPick: (prompt: string) => void
  disabled?: boolean
}

/** 快捷查询按钮组：点击后把预设 prompt 填入输入框，用户可编辑后发送 */
export default function QuickActions({ onPick, disabled }: Props) {
  return (
    <Space size={[8, 8]} wrap>
      {ACTIONS.map((a) => (
        <Tooltip key={a.key} title={a.prompt}>
          <Button
            size="small"
            icon={ICONS[a.icon ?? '']}
            onClick={() => onPick(a.prompt)}
            disabled={disabled}
          >
            {a.label}
          </Button>
        </Tooltip>
      ))}
    </Space>
  )
}
