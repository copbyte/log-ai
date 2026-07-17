import { useEffect, useState } from 'react'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'log-ai-theme'

function readInitial(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  // 默认跟随系统偏好
  if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

/** 暗色/浅色主题切换 hook，持久化到 localStorage */
export function useTheme() {
  const [mode, setMode] = useState<ThemeMode>(readInitial)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, mode)
    document.documentElement.dataset.theme = mode
  }, [mode])

  const toggle = () => setMode((m) => (m === 'dark' ? 'light' : 'dark'))

  return { mode, setMode, toggle, isDark: mode === 'dark' }
}
