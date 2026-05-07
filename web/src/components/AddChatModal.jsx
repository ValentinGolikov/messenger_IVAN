import { useState } from 'react'
import '../styles/add-chat.css'

export default function AddChatModal({ existingIds, onAdd, onClose, onSearch }) {
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState('direct')
  const [groupName, setGroupName] = useState('')
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const selectedMembers = results.filter(u => selectedIds.has(u.id))
  const canCreateGroup = groupName.trim().length >= 2 && selectedMembers.length >= 2

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
      setResults((users || []).filter(u => mode === 'group' || !existingIds.includes(u.id)))
    } catch (err) {
      setError(err.message || 'Не удалось выполнить поиск')
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  function toggleMember(userId) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      next.has(userId) ? next.delete(userId) : next.add(userId)
      return next
    })
  }

  function handleCreateGroup() {
    if (!canCreateGroup) return
    onAdd({
      kind: 'group',
      name: groupName,
      members: selectedMembers.map(u => ({
        id: u.id,
        name: u.name || u.displayName || `User ${u.id}`,
        avatar: (u.name || u.displayName || '?')[0],
      })),
    })
    onClose()
  }

  return (
    <div className="add-overlay" onClick={onClose}>
      <div className="add-modal" onClick={e => e.stopPropagation()}>
        <div className="add-header">
          <h3>Новый чат</h3>
          <button className="add-close" onClick={onClose}>✕</button>
        </div>

        <div className="add-tabs">
          <button className={mode === 'direct' ? 'active' : ''} onClick={() => setMode('direct')}>
            Личный
          </button>
          <button className={mode === 'group' ? 'active' : ''} onClick={() => setMode('group')}>
            Группа
          </button>
        </div>

        {mode === 'group' && (
          <div className="add-group-fields">
            <input
              type="text"
              placeholder="Название группы"
              value={groupName}
              onChange={e => setGroupName(e.target.value)}
            />
            <div className="add-selected-summary">
              {selectedMembers.length > 0 ? `${selectedMembers.length} участника выбрано` : 'Выберите минимум 2 участников'}
            </div>
          </div>
        )}

        <div className="add-search">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            autoFocus
            type="text"
            placeholder={mode === 'group' ? 'Найти участников...' : 'Имя, @username или ID...'}
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
            <p className="add-hint">
              {mode === 'group' ? 'Найдите и выберите участников группы' : 'Введите имя, @username или ID пользователя'}
            </p>
          )}
          {results.map(u => (
            <button
              key={u.id}
              className={`add-user-item ${selectedIds.has(u.id) ? 'selected' : ''}`}
              onClick={() => {
                if (mode === 'group') {
                  toggleMember(u.id)
                  return
                }
                onAdd(u)
                onClose()
              }}
            >
              <div className="add-user-avatar">
                <span>{(u.name || u.displayName || '?')[0]}</span>
              </div>
              <div className="add-user-info">
                <span className="add-user-name">{u.name || u.displayName}</span>
                <span className="add-user-username">@{u.username || 'user'} · ID {u.id}</span>
              </div>
              <span className="add-start">
                {mode === 'group' ? (selectedIds.has(u.id) ? 'Выбран' : 'Выбрать') : 'Начать чат →'}
              </span>
            </button>
          ))}
        </div>

        {mode === 'group' && (
          <div className="add-actions">
            <button className="add-cancel" onClick={onClose}>Отмена</button>
            <button className="add-create" disabled={!canCreateGroup} onClick={handleCreateGroup}>
              Создать группу
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
