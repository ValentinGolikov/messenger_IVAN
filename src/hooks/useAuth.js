import { useState, useEffect } from 'react'

// TODO: заменить на нормальный state manager

export function useAuth() {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem('user')
      return stored ? JSON.parse(stored) : null
    } catch {
      return null
    }
  })

  function saveUser(userData) {
    localStorage.setItem('user', JSON.stringify(userData))
    setUser(userData)
  }

  function clearUser() {
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    setUser(null)
  }

  return { user, saveUser, clearUser }
}
