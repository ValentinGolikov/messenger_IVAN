import { useState } from 'react'
import '../styles/add-chat.css'

const MOCK_USERS = [
  { id: 101, name: 'Дмитрий Новиков', username: 'dmitry_n', avatar: 'Д', online: false },
  { id: 102, name: 'Анна Белова',      username: 'anna_b',   avatar: 'А', online: true  },
  { id: 103, name: 'Сергей Попов',     username: 'sergey_p', avatar: 'С', online: false },
  { id: 104, name: 'Елена Морозова',   username: 'elena_m',  avatar: 'Е', online: true  },
  { id: 105, name: 'Николай Волков',   username: 'n_volkov', avatar: 'Н', online: false },
]

export default function AddChatModal({ existingIds, onAdd, onClose }) {
  const [query, setQuery] = useState('')

  const results = MOCK_USERS.filter(u =>
    !existingIds.includes(u.id) &&
    (u.name.toLowerCase().includes(query.toLowerCase()) ||
     u.username.toLowerCase().includes(query.toLowerCase()) ||
     String(u.id).includes(query))
  )

  return (
    <div className="add-overlay" onClick={onClose}>
      <div className="add-modal" onClick={e => e.stopPropagation()}>
        <div className="add-header">
          <h3>Новый чат</h3>
          <button className="add-close" onClick={onClose}>✕</button>
        </div>

        <div className="add-search">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            autoFocus
            type="text"
            placeholder="Имя, @username или ID..."
            value={query}
            onChange={e => setQuery(e.target.value)}
          />
        </div>

        <div className="add-results">
          {query && results.length === 0 && (
            <p className="add-empty">Пользователи не найдены</p>
          )}
          {!query && (
            <p className="add-hint">Введите имя, @username или ID пользователя</p>
          )}
          {results.map(u => (
            <button
              key={u.id}
              className="add-user-item"
              onClick={() => { onAdd(u); onClose() }}
            >
              <div className="add-user-avatar">
                <span>{u.avatar}</span>
                {u.online && <span className="add-online-dot" />}
              </div>
              <div className="add-user-info">
                <span className="add-user-name">{u.name}</span>
                <span className="add-user-username">@{u.username} · ID {u.id}</span>
              </div>
              <span className="add-start">Начать чат →</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
