import { useState, useRef, useEffect } from 'react'
import EmojiPicker from './EmojiPicker'
import ContextMenu from './ContextMenu'
import ReplyPreview from './ReplyPreview'
import ForwardModal from './ForwardModal'
import ProfileModal from './ProfileModal'
import '../styles/chat.css'

export default function ChatArea({ chat, messages, onSend, onClearHistory, chats, appearance, getAlias, setAlias, onUpdateContactAvatar }) {
  const [input, setInput]               = useState('')
  const [showEmoji, setShowEmoji]       = useState(false)
  const [showMenu, setShowMenu]         = useState(false)
  const [showClearConfirm, setShowClearConfirm] = useState(null)
  const [ctxMenu, setCtxMenu]           = useState(null)
  const [replyTo, setReplyTo]           = useState(null)
  const [showForward, setShowForward]   = useState(false)
  const [showContactProfile, setShowContactProfile] = useState(false)
  const [showScrollBtn, setShowScrollBtn] = useState(false)
  const messagesEndRef = useRef(null)
  const messagesRef    = useRef(null)
  const fileInputRef   = useRef(null)
  const menuRef        = useRef(null)

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

  function handleSend(e) {
    e?.preventDefault()
    if (!input.trim()) return
    onSend(input.trim(), null, undefined, replyTo)
    setInput('')
    setReplyTo(null)
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (input.trim()) handleSend()
    }
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
    return [
      {
        label: 'Ответить',
        icon: '↩️',
        action: () => setReplyTo(msg),
      },
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
      { label: 'Удалить сообщение', icon: '🗑️', danger: true, action: () => {} },
    ]
  }

  function handleForward(targetChatId, quoteText) {
    onSend(`> Цитата (аноним):\n${quoteText}`, null, targetChatId)
    setReplyTo(null)
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
          {msg.file ? (
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
            <span className="message-time">{msg.time}</span>
            {msg.from === 'me' && <MessageStatus status={msg.status} />}
            {chat.e2e && <span className="msg-lock"><LockTinyIcon /></span>}
          </div>
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
            {chat.e2e && (
              <span className="e2e-badge"><LockIcon /> E2E</span>
            )}
          </div>
            <span className="chat-header-status">
              {chat.online ? '🟢 в сети' : '⚫ не в сети'}
            </span>
          </div>
        </button>

        <div className="chat-header-menu" ref={menuRef}>
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
      {chat.e2e && (
        <div className="e2e-notice">
          <LockIcon />
          Сообщения защищены сквозным шифрованием (E2E). Никто, кроме участников, не может их прочитать.
        </div>
      )}

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

          {input.length > 0 && (
            <span className="char-count">{input.length}</span>
          )}

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
    </div>
  )
}

// ── Sub-components ──
function MessageStatus({ status }) {
  if (!status) return null
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

function LockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
      <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  )
}
function LockTinyIcon() {
  return (
    <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
      <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
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
