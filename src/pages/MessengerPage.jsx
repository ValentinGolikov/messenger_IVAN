import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTheme } from '../hooks/useTheme'
import { useSettings } from '../hooks/useSettings'
import Sidebar from '../components/Sidebar'
import ChatArea from '../components/ChatArea'
import SettingsPanel from '../components/SettingsPanel'
import AddChatModal from '../components/AddChatModal'
import '../styles/messenger.css'

const INITIAL_CHATS = [
  { id: 1, name: 'Иван Петров',     username: 'ivan_petrov',  lastMsg: 'Привет, как дела?',  time: '10:32', unread: 2, avatar: 'И', online: true,  e2e: true  },
  { id: 2, name: 'Команда проекта', username: 'team_project', lastMsg: 'Созвон в 15:00',       time: '09:15', unread: 0, avatar: 'К', online: false, e2e: false },
  { id: 3, name: 'Мария Сидорова',  username: 'maria_s',      lastMsg: 'Ок, договорились',     time: 'Вчера', unread: 0, avatar: 'М', online: true,  e2e: true  },
  { id: 4, name: 'Алексей Козлов',  username: 'alex_koz',     lastMsg: 'Файл отправил',         time: 'Вчера', unread: 1, avatar: 'А', online: false, e2e: true  },
]

const INITIAL_MESSAGES = {
  1: [
    { id: 1, from: 'them', text: 'Привет! 👋',                    time: '10:30', status: null },
    { id: 2, from: 'me',   text: 'Привет! Всё хорошо, спасибо 😊', time: '10:31', status: 'read' },
    { id: 3, from: 'them', text: 'Как дела с проектом?',            time: '10:32', status: null },
    { id: 4, from: 'me',   text: 'Работаем, скоро сдаём',           time: '10:33', status: 'sent' },
  ],
  2: [
    { id: 1, from: 'them', text: 'Всем привет!',              time: '09:00', status: null },
    { id: 2, from: 'them', text: 'Созвон в 15:00, не забудьте', time: '09:15', status: null },
  ],
  3: [
    { id: 1, from: 'me',   text: 'Встретимся завтра?', time: 'Вчера', status: 'read' },
    { id: 2, from: 'them', text: 'Ок, договорились',    time: 'Вчера', status: null  },
  ],
  4: [
    { id: 1, from: 'them', text: 'Держи файл', time: 'Вчера', status: null, file: { name: 'report.pdf', size: '2.4 МБ' } },
  ],
}

export default function MessengerPage() {
  const [activeChatId, setActiveChatId] = useState(1)
  const [chats, setChats]               = useState(INITIAL_CHATS)
  const [messages, setMessages]         = useState(INITIAL_MESSAGES)
  const [showSettings, setShowSettings] = useState(false)
  const [showAddChat, setShowAddChat]   = useState(false)

  const { user, clearUser }     = useAuth()
  const { theme, toggleTheme }  = useTheme()
  const { settings, update }    = useSettings()
  const navigate                = useNavigate()

  function handleLogout() {
    clearUser()
    navigate('/login')
  }

  function handleSendMessage(text, file, targetChatId) {
    const chatId = targetChatId ?? activeChatId
    const newMsg = {
      id: Date.now(),
      from: 'me',
      text,
      time: new Date().toLocaleTimeString('ru', { hour: '2-digit', minute: '2-digit' }),
      status: 'sent',
      file: file || null,
    }
    setMessages(prev => ({
      ...prev,
      [chatId]: [...(prev[chatId] || []), newMsg],
    }))
    setChats(prev => prev.map(c =>
      c.id === chatId ? { ...c, lastMsg: text || (file?.name ?? ''), time: newMsg.time } : c
    ))
  }

  function handleClearHistory() {
    setMessages(prev => ({ ...prev, [activeChatId]: [] }))
  }

  function handleAddChat(mockUser) {
    // Check if chat with this user already exists
    if (chats.find(c => c.id === mockUser.id)) {
      setActiveChatId(mockUser.id)
      return
    }
    const newChat = {
      id: mockUser.id,
      name: mockUser.name,
      username: mockUser.username,
      lastMsg: '',
      time: '',
      unread: 0,
      avatar: mockUser.avatar,
      online: mockUser.online,
      e2e: true,
    }
    setChats(prev => [...prev, newChat])
    setMessages(prev => ({ ...prev, [mockUser.id]: [] }))
    setActiveChatId(mockUser.id)
  }

  const activeChat     = chats.find(c => c.id === activeChatId)
  const activeMessages = messages[activeChatId] || []

  return (
    <div className="messenger">
      <Sidebar
        chats={chats}
        activeChatId={activeChatId}
        onSelectChat={setActiveChatId}
        user={user}
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={toggleTheme}
        onShowSettings={() => setShowSettings(true)}
        onAddChat={() => setShowAddChat(true)}
      />
      <ChatArea
        chat={activeChat}
        messages={activeMessages}
        chats={chats}
        onSend={handleSendMessage}
        onClearHistory={handleClearHistory}
      />

      {showSettings && (
        <SettingsPanel
          settings={settings}
          onUpdate={update}
          theme={theme}
          onToggleTheme={toggleTheme}
          onClose={() => setShowSettings(false)}
        />
      )}

      {showAddChat && (
        <AddChatModal
          existingIds={chats.map(c => c.id)}
          onAdd={handleAddChat}
          onClose={() => setShowAddChat(false)}
        />
      )}
    </div>
  )
}
