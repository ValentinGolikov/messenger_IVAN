import { useState } from 'react'
import '../styles/add-chat.css'

export default function AddChatModal({ existingIds, onAdd, onClose, onSearch }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleQueryChange(value) {
    setQuery(value)
    setError('')
    if (!value.trim()) {
      setResults([])
      return
    }
    try {
      setLoading(true)
      const users = await onSearch(value.trim())
      setResults((users || []).filter(u => !existingIds.includes(u.id)))
    } catch (err) {
      setError(err.message || 'Не удалось выполнить поиск')
      setResults([])
    } finally {
      setLoading(false)
    }
  }

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
            onChange={e => handleQueryChange(e.target.value)}
          />
        </div>

        <div className="add-results">
          {loading && <p className="add-hint">Поиск...</p>}
          {error && <p className="add-empty">{error}</p>}
          {query && !loading && !error && results.length === 0 && (
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
                <span>{(u.name || u.displayName || '?')[0]}</span>
              </div>
              <div className="add-user-info">
                <span className="add-user-name">{u.name || u.displayName}</span>
                <span className="add-user-username">@{u.username || 'user'} · ID {u.id}</span>
              </div>
              <span className="add-start">Начать чат →</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
