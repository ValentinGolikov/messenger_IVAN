import { useState, useEffect } from 'react'

const DEFAULTS = {
  zoom: 100,
  proxyEnabled: false,
  proxyType: 'socks5',
  proxyHost: '',
  proxyPort: '',
  notifications: true,
  language: 'ru',
  fontSize: 'medium',
  sendOnEnter: true,
}

export function useSettings() {
  const [settings, setSettings] = useState(() => {
    try {
      const stored = localStorage.getItem('settings')
      return stored ? { ...DEFAULTS, ...JSON.parse(stored) } : DEFAULTS
    } catch { return DEFAULTS }
  })

  useEffect(() => {
    localStorage.setItem('settings', JSON.stringify(settings))
    document.documentElement.style.zoom = settings.zoom + '%'
  }, [settings])

  function update(key, value) {
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  return { settings, update }
}
