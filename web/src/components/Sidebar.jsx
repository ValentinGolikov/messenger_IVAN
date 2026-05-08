import { useEffect, useState, useRef } from 'react'
import ProfileModal from './ProfileModal'
import ContextMenu from './ContextMenu'
import '../styles/sidebar.css'

export default function Sidebar({
  chats, activeChatId, onSelectChat,
  user, onLogout,
  onShowSettings, onAddChat,
  getAlias, setAlias, displayName, saveUser, onGlobalSearch,
}) {
  const [search, setSearch]         = useState('')
  const [showProfile, setShowProfile] = useState(false)
  const [pinned, setPinned]         = useState(new Set())
  const [muted, setMuted]           = useState(new Set())
  const [ctxMenu, setCtxMenu]       = useState(null)
  const [orderedIds, setOrderedIds] = useState(() => chats.map(c => c.id))
  const dragId = useRef(null)
  const dragOverId = useRef(null)

  // Merge order: pinned first (in their custom order), then unpinned
  const orderedChats = (() => {
    const chatMap = Object.fromEntries(chats.map(c => [c.id, c]))
    const ordered = orderedIds.map(id => chatMap[id]).filter(Boolean)
    // add any new chats not yet in orderedIds
    chats.forEach(c => { if (!orderedIds.includes(c.id)) ordered.push(c) })
    const pinnedList   = ordered.filter(c => pinned.has(c.id))
    const unpinnedList = ordered.filter(c => !pinned.has(c.id))
    return [...pinnedList, ...unpinnedList]
  })()

  const dmAliasByUserId = chats.reduce((acc, chat) => {
    if (chat.type !== 'dm' || !chat.otherUserId || !getAlias) return acc
    const alias = getAlias(chat.id)
    if (alias) acc[chat.otherUserId] = alias
    return acc
  }, {})

  const filtered = orderedChats.filter(c => {
    const alias = getAlias ? getAlias(c.id) : ''
    return (
      c.name.toLowerCase().includes(search.toLowerCase()) ||
      c.username.toLowerCase().includes(search.toLowerCase()) ||
      alias.toLowerCase().includes(search.toLowerCase()) ||
      c.members?.some(m => {
        const memberName = (m.name || '').toLowerCase()
        const memberAlias = (dmAliasByUserId[m.id] || '').toLowerCase()
        const term = search.toLowerCase()
        return memberName.includes(term) || memberAlias.includes(term)
      })
    )
  })

  // ── Context menu ──
  function handleContextMenu(e, chatId) {
    e.preventDefault()
    setCtxMenu({ chatId, x: e.clientX, y: e.clientY })
  }

  function buildChatMenu(chatId) {
    const isPinned = pinned.has(chatId)
    const isMuted  = muted.has(chatId)
    return [
      {
        label: isPinned ? 'Открепить' : 'Закрепить',
        icon: '📌',
        action: () => setPinned(prev => {
          const next = new Set(prev)
          isPinned ? next.delete(chatId) : next.add(chatId)
          return next
        }),
      },
      {
        label: 'Отметить как прочитанное',
        icon: '✓',
        action: () => onSelectChat(chatId),
      },
      {
        label: isMuted ? 'Включить уведомления' : 'Отключить уведомления',
        icon: isMuted ? '🔔' : '🔕',
        action: () => setMuted(prev => {
          const next = new Set(prev)
          isMuted ? next.delete(chatId) : next.add(chatId)
          return next
        }),
      },
      { divider: true },
      { label: 'Удалить чат', icon: '🗑️', danger: true, action: () => {} },
    ]
  }

  // ── Drag-and-drop (only for pinned chats) ──
  function handleDragStart(e, chatId) {
    if (!pinned.has(chatId)) { e.preventDefault(); return }
    dragId.current = chatId
    e.dataTransfer.effectAllowed = 'move'
  }

  function handleDragOver(e, chatId) {
    if (!pinned.has(chatId)) return
    e.preventDefault()
    dragOverId.current = chatId
  }

  function handleDrop(e, targetId) {
    e.preventDefault()
    const sourceId = dragId.current
    if (!sourceId || sourceId === targetId || !pinned.has(targetId)) return
    setOrderedIds(prev => {
      const arr = [...prev]
      const si = arr.indexOf(sourceId)
      const ti = arr.indexOf(targetId)
      if (si === -1 || ti === -1) return prev
      arr.splice(si, 1)
      arr.splice(ti, 0, sourceId)
      return arr
    })
    dragId.current = null
    dragOverId.current = null
  }

  function handleDragEnd() {
    dragId.current = null
    dragOverId.current = null
  }

  // Keep orderedIds in sync when chats are added/removed externally
  useEffect(() => {
    setOrderedIds(prev => {
      const incomingIds = chats.map(c => c.id)
      const kept = prev.filter(id => incomingIds.includes(id))
      const missing = incomingIds.filter(id => !kept.includes(id))
      return [...kept, ...missing]
    })
  }, [chats])

  return (
    <>
      <aside className="sidebar" onContextMenu={e => e.preventDefault()}>

        {/* Header */}
        <div className="sidebar-header">
          <button className="sidebar-user" onClick={() => setShowProfile(true)}>
            <div className="user-avatar">
              {user?.avatar
                ? <img src={user.avatar} alt={user?.displayName || user?.name} />
                : <span>{(user?.displayName || user?.name)?.[0] ?? '?'}</span>
              }
              <span className={`avatar-online-dot ${user?.online ? 'online' : ''}`} />
            </div>
            <span className="user-name">{displayName ?? 'Пользователь'}</span>
          </button>

          <div className="sidebar-actions">
            <button className="icon-btn" onClick={onAddChat} title="Новый чат">
              <PlusIcon />
            </button>
            <button className="icon-btn" onClick={onShowSettings} title="Настройки">
              <GearIcon />
            </button>
            <button className="icon-btn" onClick={onLogout} title="Выйти">
              <LogoutIcon />
            </button>
          </div>
        </div>

        {/* Search */}
        <div className="sidebar-search">
          <SearchIcon />
          <input
            type="text"
            placeholder="Поиск по имени или @username..."
            value={search}
            onChange={e => {
              const value = e.target.value
              setSearch(value)
              onGlobalSearch?.(value)
            }}
          />
          {search && (
            <button className="search-clear" onClick={() => setSearch('')}>✕</button>
          )}
        </div>

        {/* Chat list */}
        <div className="chat-list">
          {filtered.length === 0 && (
            <p className="no-results">Ничего не найдено</p>
          )}
          {filtered.map(chat => {
            const isPinned = pinned.has(chat.id)
            return (
              <button
                key={chat.id}
                className={`chat-item ${chat.id === activeChatId ? 'active' : ''} ${isPinned ? 'pinned' : ''}`}
                onClick={() => onSelectChat(chat.id)}
                onContextMenu={e => handleContextMenu(e, chat.id)}
                draggable={isPinned}
                onDragStart={e => handleDragStart(e, chat.id)}
                onDragOver={e => handleDragOver(e, chat.id)}
                onDrop={e => handleDrop(e, chat.id)}
                onDragEnd={handleDragEnd}
              >
                {isPinned && (
                  <span className="drag-handle" title="Перетащите для сортировки">⠿</span>
                )}
                <div className="chat-avatar-wrap">
                  <div className="chat-avatar">
                    {chat.customAvatar
                      ? <img src={chat.customAvatar} alt={chat.name} />
                      : chat.avatar
                    }
                  </div>
                  {chat.presenceStatus === 'online' && <span className="chat-online-dot" />}
                </div>
                <div className="chat-info">
                  <div className="chat-top">
                    <span className="chat-name">
                      {isPinned && <span className="pin-icon">📌</span>}
                      {(getAlias && getAlias(chat.id)) || chat.name}
                      <EncryptionSmallIcon status={chat.encryptionStatus} />
                    </span>
                    <span className="chat-time">{chat.time}</span>
                  </div>
                  <div className="chat-bottom">
                    <span className="chat-last-msg">
                      {muted.has(chat.id) && <span className="muted-icon">🔕 </span>}
                      {chat.lastMsg || (chat.type === 'group' ? `${chat.members?.length || 0} участников` : '')}
                    </span>
                    {chat.unread > 0 && !muted.has(chat.id) && (
                      <span className="unread-badge">{chat.unread}</span>
                    )}
                  </div>
                </div>
              </button>
            )
          })}
        </div>
      </aside>

      {ctxMenu && (
        <ContextMenu
          position={{ x: ctxMenu.x, y: ctxMenu.y }}
          items={buildChatMenu(ctxMenu.chatId)}
          onClose={() => setCtxMenu(null)}
        />
      )}

      {showProfile && (
        <ProfileModal
          user={user}
          onClose={() => setShowProfile(false)}
          isOwnProfile={true}
          onSave={newName => saveUser && saveUser({ ...user, displayName: newName }, { rememberDisplayName: true })}
          canEditAvatar={!!saveUser}
          onAvatarChange={dataUrl => saveUser && saveUser({ ...user, avatar: dataUrl })}
        />
      )}
    </>
  )
}

// ── Icons ──
function SearchIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}
function EncryptionSmallIcon({ status }) {
  const title = status === 'verified'
    ? 'Шифрование подтверждено'
    : 'E2E не активировано: требуется серверный протокол ключей'
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" style={{ marginLeft: 3, verticalAlign: 'middle', color: status === 'verified' ? 'var(--e2e-text)' : 'var(--warning)' }} title={title}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      {status === 'verified'
        ? <path d="M8.5 12.5l2.2 2.2 4.8-5" />
        : <path d="M8 8l8 8M16 8l-8 8" />
      }
    </svg>
  )
}
function PlusIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
      <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  )
}
function GearIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}
function LogoutIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  )
}
