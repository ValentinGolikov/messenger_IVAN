import { useState, useRef, useEffect } from 'react'
import EmojiPicker from './EmojiPicker'
import ContextMenu from './ContextMenu'
import ReplyPreview from './ReplyPreview'
import ForwardModal from './ForwardModal'
import ProfileModal from './ProfileModal'
import '../styles/chat.css'

export default function ChatArea({
  chat, messages, onSend, onRetry, onBack, onClearHistory, chats, appearance, getAlias, setAlias,
  onUpdateContactAvatar, onSearchUsers, onInviteToGroup, onEditMessage, onDeleteMessage, onTyping,
  typingUserId, groupMembers, onLoadGroupMembers, onSetGroupRole, selfUserId, onJoinInvite,
}) {
  const [input, setInput]               = useState('')
  const [showEmoji, setShowEmoji]       = useState(false)
  const [showMenu, setShowMenu]         = useState(false)
  const [showClearConfirm, setShowClearConfirm] = useState(null)
  const [ctxMenu, setCtxMenu]           = useState(null)
  const [replyTo, setReplyTo]           = useState(null)
  const [showForward, setShowForward]   = useState(false)
  const [showContactProfile, setShowContactProfile] = useState(false)
  const [showInviteModal, setShowInviteModal] = useState(false)
  const [inviteQuery, setInviteQuery] = useState('')
  const [inviteResults, setInviteResults] = useState([])
  const [inviteLoading, setInviteLoading] = useState(false)
  const [inviteError, setInviteError] = useState('')
  const [invitingIds, setInvitingIds] = useState(new Set())
  const [showScrollBtn, setShowScrollBtn] = useState(false)
  const [typingVisible, setTypingVisible] = useState(false)
  const messagesEndRef = useRef(null)
  const messagesRef    = useRef(null)
  const fileInputRef   = useRef(null)
  const menuRef        = useRef(null)
  const typingTimeoutRef = useRef(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    function handleClick(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) setShowMenu(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function handleScroll() {
    const el = messagesRef.current
    if (!el) return
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    setShowScrollBtn(distFromBottom > 150)
  }

  function scrollToBottom() {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    if (!chat || chat.type !== 'group') return
    onLoadGroupMembers?.(chat.id)
  }, [chat, onLoadGroupMembers])

  useEffect(() => {
    if (!chat || !typingUserId) {
      const hideT = setTimeout(() => setTypingVisible(false), 700)
      return () => clearTimeout(hideT)
    }
    const showT = setTimeout(() => setTypingVisible(true), 350)
    return () => clearTimeout(showT)
  }, [chat, typingUserId])

  if (!chat) {
    return (
      <div className="chat-area empty">
        <div className="empty-state">
          <span className="empty-icon">💬</span>
          <p>Выберите чат для начала общения</p>
        </div>
      </div>
    )
  }

  const chatName = (getAlias && getAlias(chat.id)) || chat.name
  const myRole = (groupMembers || []).find(m => m.id === selfUserId)?.role || 'member'
  const onlineCount = (groupMembers || []).filter(m => m.online).length

  function handleSend(e) {
    e?.preventDefault()
    if (!input.trim()) return
    onSend(input.trim(), null, undefined, replyTo)
    onTyping?.(chat.id, false)
    setInput('')
    setReplyTo(null)
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (input.trim()) handleSend()
      return
    }
    onTyping?.(chat.id, true)
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    typingTimeoutRef.current = setTimeout(() => onTyping?.(chat.id, false), 1400)
  }

  function handleFileChange(e) {
    const file = e.target.files[0]
    if (!file) return
    onSend('', { name: file.name, size: formatSize(file.size) })
    e.target.value = ''
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' Б'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' КБ'
    return (bytes / 1024 / 1024).toFixed(1) + ' МБ'
  }

  function handleClearConfirm(forBoth) {
    onClearHistory(forBoth)
    setShowClearConfirm(null)
    setShowMenu(false)
  }

  function handleMsgContextMenu(e, msg) {
    e.preventDefault()
    setCtxMenu({ msg, x: e.clientX, y: e.clientY })
  }

  function buildMsgMenu(msg) {
    const canEdit = msg.from === 'me' && msg.text
    const canDeleteForAll = msg.from === 'me'
    return [
      ...(msg.status === 'failed' ? [{
        label: 'Повторить отправку',
        icon: '↻',
        action: () => onRetry?.(msg),
      }, { divider: true }] : []),
      {
        label: 'Ответить',
        icon: '↩️',
        action: () => setReplyTo(msg),
      },
      ...(canEdit ? [{
        label: 'Редактировать',
        icon: '✎',
        action: async () => {
          const next = window.prompt('Новое сообщение', msg.text)
          if (!next || !next.trim() || next.trim() === msg.text) return
          await onEditMessage?.(msg.chatId, msg.id, next.trim())
        },
      }] : []),
      ...(msg.text ? [{
        label: 'Копировать текст',
        icon: '📋',
        action: () => navigator.clipboard.writeText(msg.text),
      }] : []),
      {
        label: 'Переслать',
        icon: '➡️',
        action: () => { setReplyTo(msg); setShowForward(true) },
      },
      { divider: true },
      {
        label: 'Удалить у себя',
        icon: '🗑️',
        danger: true,
        action: () => onDeleteMessage?.(msg.chatId, msg.id, false),
      },
      ...(canDeleteForAll ? [{
        label: 'Удалить у всех',
        icon: '🗑️',
        danger: true,
        action: () => onDeleteMessage?.(msg.chatId, msg.id, true),
      }] : []),
    ]
  }

  function handleForward(targetChatId, quoteText) {
    onSend(`> Цитата (аноним):\n${quoteText}`, null, targetChatId)
    setReplyTo(null)
  }

  async function handleInviteSearch(value) {
    setInviteQuery(value)
    setInviteError('')
    if (!value.trim()) {
      setInviteResults([])
      return
    }
    if (!onSearchUsers) return
    try {
      setInviteLoading(true)
      const users = await onSearchUsers(value.trim())
      const memberIds = new Set((chat.members || []).map(m => m.id))
      setInviteResults((users || []).filter(u => !memberIds.has(u.id)))
    } catch (err) {
      setInviteError(err.message || 'Не удалось выполнить поиск')
      setInviteResults([])
    } finally {
      setInviteLoading(false)
    }
  }

  async function handleInviteUser(userId) {
    if (!onInviteToGroup) return
    setInvitingIds(prev => new Set(prev).add(userId))
    setInviteError('')
    try {
      await onInviteToGroup(chat.id, userId)
      setInviteResults(prev => prev.filter(u => u.id !== userId))
    } catch (err) {
      setInviteError(err.message || 'Не удалось добавить участника')
    } finally {
      setInvitingIds(prev => {
        const next = new Set(prev)
        next.delete(userId)
        return next
      })
    }
  }

  // Date separator helpers
  function getDayLabel(timeStr) {
    if (/^\d{2}:\d{2}$/.test(timeStr)) return 'Сегодня'
    if (timeStr === 'Вчера') return 'Вчера'
    return timeStr
  }

  // Build messages with date separators
  const messageElements = []
  let lastDayLabel = null
  const encryptionCopy = getEncryptionCopy(chat.encryptionStatus)
  messages.forEach(msg => {
    const dayLabel = getDayLabel(msg.time)
    if (dayLabel !== lastDayLabel) {
      lastDayLabel = dayLabel
      messageElements.push(
        <div key={`sep-${msg.id}`} className="date-separator">{dayLabel}</div>
      )
    }
    messageElements.push(
      <div key={msg.id} className={`message ${msg.from === 'me' ? 'me' : 'them'}`}>
        <div
          className="message-bubble"
          onContextMenu={e => handleMsgContextMenu(e, msg)}
        >
          {chat.type === 'group' && msg.from !== 'me' && (
            <span className="message-sender">{msg.senderName || 'Участник'}</span>
          )}
          {msg.replyTo && (
            <div className="msg-reply-quote">
              <div className="msg-reply-bar" />
              <div className="msg-reply-body">
                <span className="msg-reply-sender">
                  {msg.replyTo.from === 'me' ? 'Вы' : chatName}
                </span>
                <span className="msg-reply-text">
                  {msg.replyTo.file ? `📎 ${msg.replyTo.file.name}` : msg.replyTo.text}
                </span>
              </div>
            </div>
          )}
          {isInviteMessage(msg.text) ? (
            <InviteMessageCard
              token={getInviteToken(msg.text)}
              senderName={msg.senderName || 'Пользователь'}
              onJoin={onJoinInvite}
            />
          ) : msg.file ? (
            <div className="file-attachment">
              <FileIcon />
              <div className="file-info">
                <span className="file-name">{msg.file.name}</span>
                <span className="file-size">{msg.file.size}</span>
              </div>
              <button className="file-download" title="Скачать">↓</button>
            </div>
          ) : (
            <p style={{ whiteSpace: 'pre-wrap' }}>{msg.text}</p>
          )}
          <div className="message-meta">
            {msg.edited && <span className="message-time">(изменено)</span>}
            <span className="message-time">{msg.time}</span>
            {msg.from === 'me' && <MessageStatus status={msg.status} />}
          </div>
          {msg.status === 'failed' && (
            <button className="message-retry" onClick={() => onRetry?.(msg)}>
              Повторить
            </button>
          )}
        </div>
      </div>
    )
  })

  // Background inline style for messages area
  const messagesStyle = {}
  if (appearance?.chatBgImage) {
    messagesStyle.backgroundImage = `url(${appearance.chatBgImage})`
    messagesStyle.backgroundSize = 'cover'
    messagesStyle.backgroundPosition = 'center'
  } else if (appearance?.chatBg) {
    messagesStyle.backgroundColor = appearance.chatBg
  }

  return (
    <div className="chat-area" onContextMenu={e => e.preventDefault()}>

      {/* Header */}
      <div className="chat-header">
        <button className="mobile-back-btn" onClick={onBack} title="К списку чатов">
          <BackIcon />
        </button>
        <button className="chat-header-identity" onClick={() => setShowContactProfile(true)}>
          <div className="chat-header-avatar">
            {chat.customAvatar
              ? <img src={chat.customAvatar} alt={chat.name} />
              : chat.avatar
            }
          </div>
          <div className="chat-header-info">
            <div className="chat-header-name-row">
              <span className="chat-header-name">{chatName}</span>
              <span className={`e2e-badge ${encryptionCopy.tone}`}>
                <ShieldIcon status={chat.encryptionStatus} />
                {encryptionCopy.short}
              </span>
          </div>
            <span className="chat-header-status">
              {typingVisible
                ? 'печатает...'
                : (chat.type === 'group'
                    ? `${groupMembers.length || chat.members?.length || 0} участников, ${onlineCount} онлайн`
                    : getPresenceCopy(chat))}
            </span>
          </div>
        </button>

        <div className="chat-header-menu" ref={menuRef}>
          {chat.type === 'group' && (
            <button className="icon-btn" onClick={() => setShowInviteModal(true)} title="Добавить участника">
              <AddUserIcon />
            </button>
          )}
          <button className="icon-btn" onClick={() => setShowMenu(v => !v)}>
            <DotsIcon />
          </button>
          {showMenu && (
            <div className="dropdown">
              <button onClick={() => { setShowClearConfirm('me'); setShowMenu(false) }}>
                Очистить у себя
              </button>
              <button onClick={() => { setShowClearConfirm('both'); setShowMenu(false) }}>
                Очистить у обоих
              </button>
            </div>
          )}
        </div>
      </div>

      {/* E2E notice */}
      <div className={`e2e-notice ${encryptionCopy.tone}`}>
        <ShieldIcon status={chat.encryptionStatus} />
        {encryptionCopy.notice}
      </div>

      {/* Messages */}
      <div
        className="messages"
        ref={messagesRef}
        onScroll={handleScroll}
        style={messagesStyle}
      >
        {messages.length === 0 && (
          <p className="no-messages">Нет сообщений. Начните общение!</p>
        )}
        {messageElements}
        <div ref={messagesEndRef} />
      </div>

      {/* Scroll-to-bottom button */}
      {showScrollBtn && (
        <button className="scroll-to-bottom-btn" onClick={scrollToBottom} title="Вниз">
          ↓
        </button>
      )}

      {/* Input area */}
      <div className="message-input-area">
        {replyTo && !showForward && (
          <ReplyPreview
            replyTo={replyTo}
            onClose={() => setReplyTo(null)}
            onClick={() => setShowForward(true)}
          />
        )}

        {showEmoji && (
          <EmojiPicker
            onSelect={e => setInput(prev => prev + e)}
            onClose={() => setShowEmoji(false)}
          />
        )}

        <form className="message-input-row" onSubmit={handleSend}>
          <button
            type="button"
            className="input-icon-btn"
            onClick={() => setShowEmoji(v => !v)}
            title="Эмодзи"
          >
            😊
          </button>

          <button
            type="button"
            className="input-icon-btn"
            onClick={() => fileInputRef.current?.click()}
            title="Прикрепить файл"
          >
            <AttachIcon />
          </button>

          <input
            type="text"
            placeholder="Написать сообщение..."
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
          />

          <button type="submit" className="send-btn" disabled={!input.trim()}>
            <SendIcon />
          </button>
        </form>

        <input
          ref={fileInputRef}
          type="file"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </div>

      {/* Message context menu */}
      {ctxMenu && (
        <ContextMenu
          position={{ x: ctxMenu.x, y: ctxMenu.y }}
          items={buildMsgMenu(ctxMenu.msg)}
          onClose={() => setCtxMenu(null)}
        />
      )}

      {/* Forward modal */}
      {showForward && replyTo && (
        <ForwardModal
          quoteText={replyTo.text || (replyTo.file ? replyTo.file.name : '')}
          chats={chats || []}
          currentChatId={chat.id}
          onForward={handleForward}
          onClose={() => setShowForward(false)}
        />
      )}

      {/* Contact profile */}
      {showContactProfile && (
        <ProfileModal
          user={{ ...chat, avatar: chat.customAvatar || null }}
          onClose={() => setShowContactProfile(false)}
          alias={getAlias ? getAlias(chat.id) : ''}
          onSetAlias={name => setAlias && setAlias(chat.id, name)}
          canEditAvatar={!!onUpdateContactAvatar}
          onAvatarChange={dataUrl => onUpdateContactAvatar?.(chat.id, dataUrl)}
        />
      )}

      {/* Clear history confirm */}
      {showClearConfirm && (
        <div className="confirm-overlay" onClick={() => setShowClearConfirm(null)}>
          <div className="confirm-dialog" onClick={e => e.stopPropagation()}>
            <h3>Очистить историю?</h3>
            <p>
              {showClearConfirm === 'me'
                ? 'Сообщения будут удалены только у вас.'
                : 'Сообщения будут удалены у обоих участников.'}
            </p>
            <div className="confirm-actions">
              <button className="confirm-cancel" onClick={() => setShowClearConfirm(null)}>Отмена</button>
              <button className="confirm-ok" onClick={() => handleClearConfirm(showClearConfirm === 'both')}>
                Удалить
              </button>
            </div>
          </div>
        </div>
      )}

      {showInviteModal && chat.type === 'group' && (
        <div className="confirm-overlay" onClick={() => setShowInviteModal(false)}>
          <div className="confirm-dialog" onClick={e => e.stopPropagation()}>
            <h3>Добавить участника</h3>
            <div className="add-search" style={{ marginTop: 10 }}>
              <SearchIcon />
              <input
                type="text"
                placeholder="Имя, @username или ID..."
                value={inviteQuery}
                onChange={e => handleInviteSearch(e.target.value)}
              />
            </div>
            <div className="add-results" style={{ marginTop: 10, maxHeight: 260 }}>
              {inviteLoading && <p className="add-hint">Поиск...</p>}
              {inviteError && <p className="add-empty">{inviteError}</p>}
              {inviteQuery && !inviteLoading && !inviteError && inviteResults.length === 0 && (
                <p className="add-empty">Нет доступных пользователей</p>
              )}
              {!inviteQuery && <p className="add-hint">Найдите пользователя для добавления в группу</p>}
              {inviteResults.map(u => {
                const pending = invitingIds.has(u.id)
                return (
                  <button
                    key={u.id}
                    className="add-user-item"
                    onClick={() => handleInviteUser(u.id)}
                    disabled={pending}
                  >
                    <div className="add-user-avatar">
                      <span>{(u.name || u.displayName || '?')[0]}</span>
                    </div>
                    <div className="add-user-info">
                      <span className="add-user-name">{u.name || u.displayName}</span>
                      <span className="add-user-username">@{u.username || 'user'} · ID {u.id}</span>
                    </div>
                    <span className="add-start">{pending ? 'Добавление...' : 'Добавить'}</span>
                  </button>
                )
              })}
            </div>
            {(myRole === 'owner') && groupMembers.length > 0 && (
              <div style={{ marginTop: 12 }}>
                <p className="add-hint" style={{ marginBottom: 6 }}>Роли участников</p>
                <div className="add-results" style={{ maxHeight: 200 }}>
                  {groupMembers.map(member => (
                    <div key={member.id} className="add-user-item" style={{ cursor: 'default' }}>
                      <div className="add-user-avatar">
                        <span>{(member.displayName || '?')[0]}</span>
                      </div>
                      <div className="add-user-info">
                        <span className="add-user-name">{member.displayName}</span>
                        <span className="add-user-username">ID {member.id} · {member.role}</span>
                      </div>
                      {member.role !== 'owner' && (
                        <div className="add-start" style={{ display: 'flex', gap: 8 }}>
                          {member.role !== 'admin' && <button onClick={() => onSetGroupRole?.(chat.id, member.id, 'admin')}>Сделать админом</button>}
                          {member.role !== 'member' && <button onClick={() => onSetGroupRole?.(chat.id, member.id, 'member')}>Сделать участником</button>}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
            <div className="confirm-actions">
              <button className="confirm-cancel" onClick={() => setShowInviteModal(false)}>Закрыть</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Sub-components ──
function MessageStatus({ status }) {
  if (!status) return null
  if (status === 'pending') {
    return <span className="msg-status pending" title="Отправляется">…</span>
  }
  if (status === 'failed') {
    return <span className="msg-status failed" title="Не отправлено">!</span>
  }
  if (status === 'read') {
    return (
      <span className="msg-status read" title="Прочитано">
        <svg width="14" height="10" viewBox="0 0 18 12" fill="none">
          <path d="M1 6l4 4L16 1" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <path d="M6 10l4-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </svg>
      </span>
    )
  }
  return (
    <span className="msg-status sent" title="Отправлено">
      <svg width="12" height="10" viewBox="0 0 14 12" fill="none">
        <path d="M1 6l4 4L13 1" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      </svg>
    </span>
  )
}

function ShieldIcon({ status }) {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      {status === 'verified'
        ? <path d="M8.5 12.5l2.2 2.2 4.8-5" />
        : <path d="M8 8l8 8M16 8l-8 8" />
      }
    </svg>
  )
}

function BackIcon() {
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
      <path d="M19 12H5" />
      <path d="M12 19l-7-7 7-7" />
    </svg>
  )
}

function getEncryptionCopy(status) {
  if (status === 'verified') {
    return {
      tone: 'secure',
      short: 'E2E',
      notice: 'Сквозное шифрование активно и ключи подтверждены.',
    }
  }
  return {
    tone: 'warning',
    short: 'E2E не активно',
    notice: 'Сквозное шифрование пока не активировано: для выполнения требования заказчика нужен серверный протокол ключей и подтверждение устройств.',
  }
}

function getPresenceCopy(chat) {
  if (chat.presenceStatus === 'online') return '🟢 в сети'
  if (chat.presenceStatus === 'offline') {
    if (chat.lastSeen) {
      const t = new Date(chat.lastSeen)
      return `был(а) в сети ${t.toLocaleTimeString('ru', { hour: '2-digit', minute: '2-digit' })}`
    }
    return '⚫ не в сети'
  }
  return 'статус неизвестен'
}

function isInviteMessage(text) {
  return typeof text === 'string' && text.startsWith('/join/')
}

function getInviteToken(text) {
  return isInviteMessage(text) ? text.slice('/join/'.length).trim() : ''
}

function InviteMessageCard({ token, senderName, onJoin }) {
  const [joining, setJoining] = useState(false)
  const [state, setState] = useState('ready') // ready | joined | failed

  async function handleJoin() {
    if (!token || joining) return
    try {
      setJoining(true)
      await onJoin?.(token)
      setState('joined')
    } catch {
      setState('failed')
    } finally {
      setJoining(false)
    }
  }

  return (
    <div className="invite-card">
      <div className="invite-card-head">
        <span className="invite-icon">👥</span>
        <div className="invite-meta">
          <span className="invite-title">Приглашение в групповой чат</span>
          <span className="invite-sub">{senderName} приглашает вас присоединиться</span>
        </div>
      </div>
      <div className="invite-card-actions">
        <button className="invite-join-btn" onClick={handleJoin} disabled={joining || state === 'joined'}>
          {state === 'joined' ? 'Вы вступили' : (joining ? 'Вступаем...' : 'Вступить')}
        </button>
        {state === 'failed' && <span className="invite-error">Не удалось вступить</span>}
      </div>
    </div>
  )
}

function DotsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
      <circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>
    </svg>
  )
}
function AttachIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l8.49-8.48" />
    </svg>
  )
}
function SendIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
    </svg>
  )
}
function FileIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </svg>
  )
}

function AddUserIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M19 8v6M22 11h-6" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="8" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}
