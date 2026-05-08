import { useState, useEffect } from 'react'

const DEFAULTS = {
  zoom: 100,
  proxyEnabled: false,
  proxyType: 'socks5',
  proxyHost: '',
  proxyPort: '',
  notifications: true,
  language: 'ru',
}

function sanitizeSettings(raw) {
  if (!raw || typeof raw !== 'object') return { ...DEFAULTS }
  return {
    zoom: Number(raw.zoom) || DEFAULTS.zoom,
    proxyEnabled: Boolean(raw.proxyEnabled),
    proxyType: raw.proxyType || DEFAULTS.proxyType,
    proxyHost: raw.proxyHost || DEFAULTS.proxyHost,
    proxyPort: raw.proxyPort || DEFAULTS.proxyPort,
    notifications: raw.notifications ?? DEFAULTS.notifications,
    language: raw.language || DEFAULTS.language,
  }
}

export function useSettings() {
  const [settings, setSettings] = useState(() => {
    try {
      const stored = localStorage.getItem('settings')
      return stored ? sanitizeSettings(JSON.parse(stored)) : { ...DEFAULTS }
    } catch { return { ...DEFAULTS } }
  })

  useEffect(() => {
    localStorage.setItem('settings', JSON.stringify(sanitizeSettings(settings)))
    const zoomFactor = Math.max(0.75, Math.min(1.5, settings.zoom / 100))
    document.documentElement.style.setProperty('--app-zoom', String(zoomFactor))
  }, [settings])

  function update(key, value) {
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  return { settings, update }
}
