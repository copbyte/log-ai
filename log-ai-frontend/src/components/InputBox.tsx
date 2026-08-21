import { Input, Button, Space } from 'antd'
import { SendOutlined, ClearOutlined, AudioOutlined } from '@ant-design/icons'
import type { VoiceState } from '@/hooks/useVoiceAssistant'

const { TextArea } = Input

interface Props {
  value: string
  onChange: (v: string) => void
  onSend: () => void
  onClear: () => void
  loading: boolean
  placeholder?: string
  // 语音助手（可选：不传则隐藏麦克风按钮）
  voiceState?: VoiceState
  voiceSupported?: boolean
  onVoiceToggle?: () => void
}

const VOICE_LABEL: Record<VoiceState, string> = {
  idle: '语音',
  listening: '聆听中…',
  wake: '已唤醒',
  processing: '思考中…',
  speaking: '播报中…',
}

/** 输入框 + 发送按钮（Enter 发送，Shift+Enter 换行） */
export default function InputBox({
  value,
  onChange,
  onSend,
  onClear,
  loading,
  placeholder,
  voiceState,
  voiceSupported,
  onVoiceToggle,
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
        <div style={{ position: 'relative', flex: 1, minWidth: 0 }}>
          <TextArea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder ?? '输入你的问题，例如：搜索最近 ERROR 日志'}
            maxLength={1000}
            autoSize={{ minRows: 1, maxRows: 6 }}
            style={{ borderRadius: '8px 0 0 8px' }}
            disabled={loading}
          />
          {/* 计数在输入框内部右下角：绝对定位，不改变输入框尺寸，也不在外部占行 */}
          <span
            style={{
              position: 'absolute',
              right: 10,
              bottom: 4,
              fontSize: 11,
              lineHeight: 1,
              opacity: 0.55,
              color: value.length >= 1000 ? '#ff4d4f' : 'inherit',
              pointerEvents: 'none',
            }}
          >
            {value.length}/1000
          </span>
        </div>
        {/* 语音按钮：只在父组件传入 voice props 时显示 */}
        {onVoiceToggle && (
          <Button
            icon={<AudioOutlined />}
            onClick={onVoiceToggle}
            danger={voiceState !== undefined && voiceState !== 'idle'}
            disabled={!voiceSupported || loading}
            title="语音对话（需授权麦克风）"
            style={{ borderRadius: 8, marginRight: 8 }}
          >
            {VOICE_LABEL[voiceState ?? 'idle']}
          </Button>
        )}
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
