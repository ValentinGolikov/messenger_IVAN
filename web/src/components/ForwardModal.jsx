import { useState } from 'react'
import '../styles/forward-modal.css'

export default function ForwardModal({ quoteText, chats, currentChatId, onForward, onClose }) {
  const [selected, setSelected] = useState(null)

  // Имена скрыты — показываем только порядковый номер чата
  const anonymousChats = chats
    .filter(c => c.id !== currentChatId)
    .map((c, i) => ({ id: c.id, label: `Диалог ${i + 1}` }))

  function handleForward() {
    if (!selected) return
    onForward(selected, quoteText)
    onClose()
  }

  return (
    <div className="fwd-overlay" onClick={onClose}>
      <div className="fwd-modal" onClick={e => e.stopPropagation()}>
        <div className="fwd-header">
          <h3>Переслать как цитату</h3>
          <button className="fwd-close" onClick={onClose}>✕</button>
        </div>

        <div className="fwd-quote-preview">
          <div className="fwd-quote-bar" />
          <div>
            <span className="fwd-quote-sender">Аноним</span>
            <p className="fwd-quote-text">{quoteText}</p>
          </div>
        </div>

        <p className="fwd-label">Выберите чат (имена скрыты):</p>

        <div className="fwd-chat-list">
          {anonymousChats.map(c => (
            <button
              key={c.id}
              className={`fwd-chat-item ${selected === c.id ? 'selected' : ''}`}
              onClick={() => setSelected(c.id)}
            >
              <div className="fwd-chat-avatar">
                {c.label[0]}
              </div>
              <span>{c.label}</span>
              {selected === c.id && <span className="fwd-check">✓</span>}
            </button>
          ))}
        </div>

        <div className="fwd-actions">
          <button className="fwd-cancel" onClick={onClose}>Отмена</button>
          <button
            className="fwd-send"
            disabled={!selected}
            onClick={handleForward}
          >
            Переслать
          </button>
        </div>
      </div>
    </div>
  )
}
