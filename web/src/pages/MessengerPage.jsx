import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTheme } from '../hooks/useTheme'
import { useSettings } from '../hooks/useSettings'
import { useAppearance } from '../hooks/useAppearance'
import { useContactAliases } from '../hooks/useContactAliases'
import { useNetworkStatus } from '../hooks/useNetworkStatus'
import {
  apiCreateDm,
  apiGetChats,
  apiGetMessages,
  apiSearchUsers,
  getApiBaseUrl,
} from '../lib/api'
import Sidebar from '../components/Sidebar'
import ChatArea from '../components/ChatArea'
import SettingsPanel from '../components/SettingsPanel'
import AddChatModal from '../components/AddChatModal'
import '../styles/messenger.css'

const PENDING_KEY = 'pending_messages_v1'

function formatMessageTime(ts) {
  return new Date(ts).toLocaleTimeString('ru', { hour: '2-digit', minute: '2-digit' })
}

function mapChat(chat) {
  const name = chat.type === 'group'
    ? (chat.title || `Группа #${chat.id}`)
    : (chat.otherUserName || `Пользователь #${chat.otherUserId ?? chat.id}`)

  return {
    id: chat.id,
    type: chat.type,
    name,
    username: chat.type === 'group' ? `group_${chat.id}` : `user_${chat.otherUserId ?? chat.id}`,
    email: '',
    lastMsg: chat.lastMessage?.text || '',
    time: chat.lastMessage ? formatMessageTime(chat.lastMessage.timestamp) : '',
    unread: chat.unreadCount || 0,
    avatar: name[0] || '?',
    presenceStatus: 'unknown',
    encryptionStatus: 'not_configured',
    members: chat.type === 'group' ? (chat.members || []) : undefined,
  }
}

function mapMessage(msg, selfId) {
  return {
    id: msg.id,
    chatId: msg.chatId,
    from: msg.senderId === selfId ? 'me' : 'them',
    text: msg.text,
    time: formatMessageTime(msg.timestamp),
    status: msg.senderId === selfId ? 'sent' : null,
    timestamp: msg.timestamp,
    senderId: msg.senderId,
    senderName: msg.senderName || null,
  }
}

function loadPending() {
  try { return JSON.parse(localStorage.getItem(PENDING_KEY) || '[]') } catch { return [] }
}

function savePending(queue) {
  localStorage.setItem(PENDING_KEY, JSON.stringify(queue))
}

function loadContactAvatars() {
  try { return JSON.parse(localStorage.getItem('contact_avatars') || '{}') } catch { return {} }
}

function withContactAvatars(chats) {
  const stored = loadContactAvatars()
  return chats.map(c => ({ ...c, customAvatar: stored[c.id] ?? null }))
}

export default function MessengerPage() {
  const [activeChatId, setActiveChatId] = useState(null)
  const [chats, setChats] = useState([])
  const [messages, setMessages] = useState({})
  const [loadedChats, setLoadedChats] = useState(() => new Set())
  const [showSettings, setShowSettings] = useState(false)
  const [showAddChat, setShowAddChat] = useState(false)

  const { user, saveUser, clearUser, displayName } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const { settings, update } = useSettings()
  const { appearance, updateAppearance, resetAppearance } = useAppearance()
  const { getAlias, setAlias } = useContactAliases()
  const isNetworkOnline = useNetworkStatus()
  const navigate = useNavigate()
  const currentUser = user ? { ...user, online: isNetworkOnline } : user

  const wsRef = useRef(null)
  const reconnectTimerRef = useRef(null)
  const reconnectAttemptRef = useRef(0)
  const pendingQueueRef = useRef(loadPending())

  const activeMessages = useMemo(() => messages[activeChatId] || [], [messages, activeChatId])

  useEffect(() => {
    if (!user?.id) return
    let cancelled = false
    ;(async () => {
      try {
        const data = await apiGetChats(user.id)
        if (cancelled) return
        const mapped = withContactAvatars((data || []).map(mapChat))
        setChats(mapped)
        if (!activeChatId && mapped.length) setActiveChatId(mapped[0].id)
      } catch (err) {
        console.error('Failed to load chats:', err)
      }
    })()
    return () => { cancelled = true }
  }, [user?.id])

  useEffect(() => {
    if (!activeChatId || loadedChats.has(activeChatId)) return
    if (!user?.id) return
    let cancelled = false
    ;(async () => {
      try {
        const data = await apiGetMessages(activeChatId)
        if (cancelled) return
        const mapped = (data || []).map(m => mapMessage(m, user.id))
        setMessages(prev => ({ ...prev, [activeChatId]: mapped }))
        setLoadedChats(prev => new Set(prev).add(activeChatId))
      } catch (err) {
        console.error('Failed to load messages:', err)
      }
    })()
    return () => { cancelled = true }
  }, [activeChatId, loadedChats, user?.id])

  useEffect(() => {
    if (!user?.id) return
    connectWs()
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      if (wsRef.current) wsRef.current.close()
    }
  }, [user?.id])

  function connectWs() {
    const raw = getApiBaseUrl()
    let base
    if (raw.startsWith('http')) {
      base = raw.replace(/^http/, 'ws')
    } else {
      const proto = location.protocol === 'https:' ? 'wss' : 'ws'
      base = `${proto}://${location.host}${raw}`
    }
    const ws = new WebSocket(`${base}/chat/${user.id}`)
    wsRef.current = ws

    ws.onopen = () => {
      reconnectAttemptRef.current = 0
      flushPending()
    }

    ws.onmessage = (event) => {
      try {
        const env = JSON.parse(event.data)
        if (env.type !== 'message') return
        const dto = typeof env.payload === 'string' ? JSON.parse(env.payload) : env.payload
        const msg = mapMessage(dto, user.id)

        setMessages(prev => {
          const current = prev[msg.chatId] || []
          if (current.some(m => m.id === msg.id)) return prev
          return { ...prev, [msg.chatId]: [...current, msg] }
        })

        setChats(prev => {
          const found = prev.find(c => c.id === msg.chatId)
          if (!found) return prev
          return prev.map(c => c.id === msg.chatId
            ? { ...c, lastMsg: msg.text, time: msg.time }
            : c
          )
        })
      } catch (err) {
        console.error('WS parse error:', err)
      }
    }

    ws.onclose = () => {
      reconnectAttemptRef.current += 1
      const delay = Math.min(30000, 1000 * (2 ** reconnectAttemptRef.current))
      reconnectTimerRef.current = setTimeout(connectWs, delay)
    }
  }

  function sendOrQueue(payload) {
    const ws = wsRef.current
    const raw = JSON.stringify(payload)
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(raw)
      return true
    }
    pendingQueueRef.current.push(payload)
    savePending(pendingQueueRef.current)
    return false
  }

  function flushPending() {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    const queue = [...pendingQueueRef.current]
    pendingQueueRef.current = []
    savePending([])
    queue.forEach(item => ws.send(JSON.stringify(item)))
  }

  function handleLogout() {
    clearUser()
    navigate('/login')
  }

  function handleSendMessage(text, file, targetChatId, replyTo, retryMessageId = null) {
    const chatId = targetChatId ?? activeChatId
    if (!chatId) return
    const payloadText = text || (file ? `[ФАЙЛ] ${file.name} (${file.size})` : '')
    if (!payloadText.trim()) return

    const time = formatMessageTime(Date.now())

    if (retryMessageId) {
      setMessages(prev => ({
        ...prev,
        [chatId]: (prev[chatId] || []).map(msg =>
          msg.id === retryMessageId ? { ...msg, status: 'pending' } : msg
        ),
      }))
    } else {
      const optimistic = {
        id: `local-${Date.now()}`,
        chatId,
        from: 'me',
        text: payloadText,
        time,
        status: 'pending',
        file: file || null,
        replyTo: replyTo || null,
        timestamp: Date.now(),
      }
      retryMessageId = optimistic.id
      setMessages(prev => ({ ...prev, [chatId]: [...(prev[chatId] || []), optimistic] }))
      setChats(prev => prev.map(c => c.id === chatId ? { ...c, lastMsg: payloadText, time } : c))
    }

    const payload = {
      chatId,
      text: payloadText,
      timestamp: Date.now(),
    }
    const sent = sendOrQueue(payload)
    setMessages(prev => ({
      ...prev,
      [chatId]: (prev[chatId] || []).map(msg =>
        msg.id === retryMessageId ? { ...msg, status: sent ? 'sent' : 'failed' } : msg
      ),
    }))
  }

  function handleRetryMessage(message) {
    if (!activeChatId || message.status !== 'failed') return
    handleSendMessage(message.text, message.file, activeChatId, message.replyTo, message.id)
  }

  function handleClearHistory() {
    if (!activeChatId) return
    setMessages(prev => ({ ...prev, [activeChatId]: [] }))
  }

  async function handleSearchUsers(query) {
    if (!user?.id) return []
    const users = await apiSearchUsers(query, user.id)
    return (users || []).map(u => ({
      id: u.id,
      name: u.displayName,
      username: String(u.id),
      avatar: (u.displayName || '?')[0],
      presenceStatus: 'unknown',
    }))
  }

  async function handleAddChat(foundUser) {
    if (!user?.id) return
    if (foundUser.kind === 'group') {
      const id = Date.now()
      const name = foundUser.name.trim()
      const group = {
        id,
        type: 'group',
        name,
        username: `group_${id}`,
        email: '',
        lastMsg: '',
        time: '',
        unread: 0,
        avatar: name[0]?.toUpperCase() || 'Г',
        presenceStatus: 'unknown',
        encryptionStatus: 'not_configured',
        members: [
          { id: user.id, name: displayName || 'Вы', avatar: (displayName || 'В')[0] },
          ...foundUser.members,
        ],
      }
      setChats(prev => [...prev, group])
      setMessages(prev => ({ ...prev, [id]: [] }))
      setActiveChatId(id)
      return
    }

    const dm = await apiCreateDm(user.id, foundUser.id)
    const data = await apiGetChats(user.id)
    const mapped = withContactAvatars((data || []).map(mapChat))
    setChats(mapped)
    if (dm?.chatId) setActiveChatId(dm.chatId)
  }

  function handleUpdateContactAvatar(chatId, dataUrl) {
    setChats(prev => prev.map(c => c.id === chatId ? { ...c, customAvatar: dataUrl } : c))
    try {
      const stored = loadContactAvatars()
      if (dataUrl) stored[chatId] = dataUrl
      else delete stored[chatId]
      localStorage.setItem('contact_avatars', JSON.stringify(stored))
    } catch {
      // ignore storage errors
    }
  }

  const activeChat = chats.find(c => c.id === activeChatId)

  return (
    <div className={`messenger ${activeChat ? 'mobile-chat-open' : ''}`}>
      <Sidebar
        chats={chats}
        activeChatId={activeChatId}
        onSelectChat={setActiveChatId}
        user={currentUser}
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={toggleTheme}
        onShowSettings={() => setShowSettings(true)}
        onAddChat={() => setShowAddChat(true)}
        getAlias={getAlias}
        setAlias={setAlias}
        displayName={displayName}
        saveUser={saveUser}
      />

      <ChatArea
        chat={activeChat}
        messages={activeMessages}
        chats={chats}
        onSend={handleSendMessage}
        onRetry={handleRetryMessage}
        onBack={() => setActiveChatId(null)}
        onClearHistory={handleClearHistory}
        appearance={appearance}
        getAlias={getAlias}
        setAlias={setAlias}
        onUpdateContactAvatar={handleUpdateContactAvatar}
      />

      {showSettings && (
        <SettingsPanel
          settings={settings}
          onUpdate={update}
          theme={theme}
          onToggleTheme={toggleTheme}
          onClose={() => setShowSettings(false)}
          appearance={appearance}
          onUpdateAppearance={updateAppearance}
          onResetAppearance={resetAppearance}
        />
      )}

      {showAddChat && (
        <AddChatModal
          existingIds={chats.map(c => c.id)}
          onAdd={handleAddChat}
          onSearch={handleSearchUsers}
          onClose={() => setShowAddChat(false)}
        />
      )}
    </div>
  )
}
