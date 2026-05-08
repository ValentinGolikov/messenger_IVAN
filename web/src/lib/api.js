const API_URL = import.meta.env.VITE_API_URL || '/api'

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

export function apiPasswordLogin(login, password) {
  return request('/auth/password/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ login, password }),
  })
}

export function apiPasswordRegister(login, password, displayName) {
  return request('/auth/password/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ login, password, displayName }),
  })
}

export function apiGetChats(userId) {
  return request(`/chats/${userId}`)
}

export function apiGetMessages(chatId) {
  return request(`/chats/${chatId}/messages`)
}

export function apiGetMessagesForUser(chatId, userId) {
  const q = new URLSearchParams({ userId: String(userId) })
  return request(`/chats/${chatId}/messages?${q.toString()}`)
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

export function apiCreateGroup(userId, title) {
  return request('/chats/group', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ userId, title }),
  })
}

export function apiInviteUserToGroup(chatId, inviterId, targetId) {
  return request(`/chats/${chatId}/invite-user`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ inviterId, targetId }),
  })
}

export function apiEditMessage(chatId, messageId, userId, text) {
  return request(`/chats/${chatId}/messages/${messageId}/edit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ userId, text }),
  })
}

export function apiDeleteMessage(chatId, messageId, userId, forAll = false) {
  return request(`/chats/${chatId}/messages/${messageId}/delete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ userId, forAll }),
  })
}

export function apiGlobalSearch(query, userId) {
  const q = new URLSearchParams({ q: query, userId: String(userId) })
  return request(`/search/global?${q.toString()}`)
}

export function apiGetPresence(ids) {
  const q = new URLSearchParams({ ids: ids.join(',') })
  return request(`/users/presence?${q.toString()}`)
}

export function apiGetGroupMembers(chatId) {
  return request(`/chats/${chatId}/members`)
}

export function apiSetGroupRole(chatId, userId, targetId, role) {
  return request(`/chats/${chatId}/set-role`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm({ userId, targetId, role }),
  })
}

export function getApiBaseUrl() {
  return API_URL
}
