import { useState, useEffect } from 'react'

export function useTheme() {
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'auto')

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const resolvedTheme = theme === 'auto'
      ? (media.matches ? 'dark' : 'light')
      : theme
    document.documentElement.setAttribute('data-theme', resolvedTheme)
    localStorage.setItem('theme', theme)
    if (theme !== 'auto') return

    function handleSystemThemeChange(e) {
      document.documentElement.setAttribute('data-theme', e.matches ? 'dark' : 'light')
    }
    media.addEventListener('change', handleSystemThemeChange)
    return () => media.removeEventListener('change', handleSystemThemeChange)
  }, [theme])

  return { theme, setTheme }
}
