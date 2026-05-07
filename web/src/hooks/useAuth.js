import { useState } from 'react'

// TODO: заменить на нормальный state manager
const USER_KEY = 'user'
const TOKEN_KEY = 'token'
const PROFILE_NAMES_KEY = 'user_display_names_v1'

function readJson(key, fallback) {
  try {
    const stored = localStorage.getItem(key)
    return stored ? JSON.parse(stored) : fallback
  } catch {
    return fallback
  }
}

function getSavedDisplayName(userId) {
  if (userId === undefined || userId === null) return null
  const names = readJson(PROFILE_NAMES_KEY, {})
  return names[String(userId)] || null
}

function rememberDisplayName(userId, name) {
  if (userId === undefined || userId === null) return
  const trimmedName = String(name || '').trim()
  const names = readJson(PROFILE_NAMES_KEY, {})

  if (trimmedName) names[String(userId)] = trimmedName
  else delete names[String(userId)]

  localStorage.setItem(PROFILE_NAMES_KEY, JSON.stringify(names))
}

function withSavedDisplayName(userData) {
  if (!userData) return null
  const savedDisplayName = getSavedDisplayName(userData.id)
  return savedDisplayName
    ? { ...userData, displayName: savedDisplayName }
    : userData
}

export function useAuth() {
  const [user, setUser] = useState(() => {
    return withSavedDisplayName(readJson(USER_KEY, null))
  })

  function saveUser(userData, options = {}) {
    const nextUser = options.rememberDisplayName
      ? { ...userData, displayName: String(userData.displayName || userData.name || '').trim() }
      : withSavedDisplayName(userData)

    if (options.rememberDisplayName) {
      rememberDisplayName(userData.id, nextUser.displayName)
    }

    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    setUser(nextUser)
  }

  function clearUser() {
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(TOKEN_KEY)
    setUser(null)
  }

  const displayName = user?.displayName || user?.name || 'Пользователь'

  return { user, saveUser, clearUser, displayName }
}
