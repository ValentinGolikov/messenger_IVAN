import { useState, useEffect } from 'react'

const DEFAULT_APPEARANCE = {
  chatBg: '',
  chatBgImage: '',
  msgMeBg: '',
  msgMeText: '',
  msgThemBg: '',
  msgThemText: '',
}

const CSS_VAR_MAP = {
  msgMeBg:    '--msg-me-bg',
  msgMeText:  '--msg-me-text',
  msgThemBg:  '--msg-them-bg',
  msgThemText: '--msg-them-text',
}

export function useAppearance() {
  const [appearance, setAppearance] = useState(() => {
    try {
      const stored = localStorage.getItem('appearance')
      return stored ? { ...DEFAULT_APPEARANCE, ...JSON.parse(stored) } : { ...DEFAULT_APPEARANCE }
    } catch {
      return { ...DEFAULT_APPEARANCE }
    }
  })

  useEffect(() => {
    Object.entries(CSS_VAR_MAP).forEach(([key, cssVar]) => {
      const val = appearance[key]
      if (val) {
        document.documentElement.style.setProperty(cssVar, val)
      } else {
        document.documentElement.style.removeProperty(cssVar)
      }
    })
  }, [appearance])

  function updateAppearance(key, value) {
    setAppearance(prev => {
      const next = { ...prev, [key]: value }
      localStorage.setItem('appearance', JSON.stringify(next))
      return next
    })
  }

  function resetAppearance() {
    setAppearance({ ...DEFAULT_APPEARANCE })
    localStorage.removeItem('appearance')
    Object.values(CSS_VAR_MAP).forEach(cssVar => {
      document.documentElement.style.removeProperty(cssVar)
    })
  }

  return { appearance, updateAppearance, resetAppearance }
}
