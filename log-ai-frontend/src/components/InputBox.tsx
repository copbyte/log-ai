import { Input, Button, Space } from 'antd'
import { SendOutlined, ClearOutlined } from '@ant-design/icons'

const { TextArea } = Input

interface Props {
  value: string
  onChange: (v: string) => void
  onSend: () => void
  onClear: () => void
  loading: boolean
  placeholder?: string
}

/** 输入框 + 发送按钮（Enter 发送，Shift+Enter 换行） */
export default function InputBox({
  value,
  onChange,
  onSend,
  onClear,
  loading,
  placeholder,
}: Props) {
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter 发送，Shift+Enter 换行
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      if (!loading && value.trim()) onSend()
    }
  }

  return (
    <div style={{ borderTop: '1px solid var(--border-color, #f0f0f0)', padding: 12 }}>
      <Space.Compact style={{ width: '100%', alignItems: 'stretch' }}>
        <TextArea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder ?? '输入你的问题，例如：搜索最近 ERROR 日志'}
          autoSize={{ minRows: 1, maxRows: 6 }}
          style={{ borderRadius: '8px 0 0 8px' }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={onSend}
          loading={loading}
          disabled={!value.trim()}
          style={{ borderRadius: '0 8px 8px 0', height: 'auto' }}
        >
          发送
        </Button>
        <Button
          icon={<ClearOutlined />}
          onClick={onClear}
          disabled={loading}
          style={{ marginLeft: 8, borderRadius: 8 }}
        >
          清空
        </Button>
      </Space.Compact>
    </div>
  )
}
