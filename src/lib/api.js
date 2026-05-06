const API_URL = import.meta.env.VITE_API_URL || 'https://titlo10.fun:8080'

function encodeForm(data) {
  const body = new URLSearchParams()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null) body.set(key, String(value))
  })
  return body
}

async function request(path, options = {}) {
  const res = await fetch(`${API_URL}${path}`, options)
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `HTTP ${res.status}`)
  }
  const ct = res.headers.get('content-type') || ''
  if (!ct.includes('application/json')) return null
  return res.json()
}

export function apiLogin(token) {
  return request('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ token }),
  })
}

export function apiGetChats(userId) {
  return request(`/chats/${userId}`)
}

export function apiGetMessages(chatId) {
  return request(`/chats/${chatId}/messages`)
}

export function apiSearchUsers(query, selfId) {
  const q = new URLSearchParams({ q: query, selfId: String(selfId) })
  return request(`/users/search?${q.toString()}`)
}

export function apiCreateDm(userId, otherUserId) {
  return request('/chats/dm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ userId, otherUserId }),
  })
}

export function getApiBaseUrl() {
  return API_URL
}
