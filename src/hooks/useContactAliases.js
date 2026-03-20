import { useState } from 'react'

export function useContactAliases() {
  const [aliases, setAliases] = useState(() => {
    try {
      const stored = localStorage.getItem('contactAliases')
      return stored ? JSON.parse(stored) : {}
    } catch {
      return {}
    }
  })

  function getAlias(id) {
    return aliases[id] || ''
  }

  function setAlias(id, name) {
    setAliases(prev => {
      const next = { ...prev, [id]: name }
      localStorage.setItem('contactAliases', JSON.stringify(next))
      return next
    })
  }

  function removeAlias(id) {
    setAliases(prev => {
      const next = { ...prev }
      delete next[id]
      localStorage.setItem('contactAliases', JSON.stringify(next))
      return next
    })
  }

  return { getAlias, setAlias, removeAlias }
}
