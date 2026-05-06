import { useState, useEffect, useRef } from 'react'
import '../styles/profile.css'

export default function ProfileModal({ user, onClose, isOwnProfile, onSave, alias, onSetAlias, canEditAvatar, onAvatarChange }) {
  const ref = useRef(null)
  const fileInputRef = useRef(null)

  const initialName = isOwnProfile
    ? (user?.displayName || user?.name || '')
    : (alias || '')

  const [editName, setEditName] = useState(initialName)
  const [dirty, setDirty]       = useState(false)
  const [saved, setSaved]       = useState(false)

  useEffect(() => {
    function handleKey(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  function handleSaveOwnName() {
    if (!dirty) return
    onSave && onSave(editName)
    setDirty(false)
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  function handleAvatarFileChange(e) {
    const file = e.target.files[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => onAvatarChange?.(ev.target.result)
    reader.readAsDataURL(file)
    e.target.value = ''
  }

  function handleSaveAlias() {
    onSetAlias && onSetAlias(editName)
    setDirty(false)
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  return (
    <div className="profile-overlay" onClick={onClose}>
      <div className="profile-modal" ref={ref} onClick={e => e.stopPropagation()}>
        <button className="profile-close" onClick={onClose}>✕</button>

        <div className="profile-avatar-wrapper">
          <div className="profile-avatar-big">
            {user?.avatar
              ? <img src={user.avatar} alt={user.name} />
              : <span className="profile-avatar-letter">{user?.name?.[0] ?? '?'}</span>
            }
            {canEditAvatar && (
              <button
                className="profile-avatar-edit"
                onClick={() => fileInputRef.current?.click()}
                title={isOwnProfile ? 'Сменить фото' : 'Установить своё фото'}
              >
                <CameraIcon />
              </button>
            )}
          </div>
          <span className={`profile-online-dot ${user?.online ? 'online' : ''}`} />
        </div>

        {canEditAvatar && (
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={handleAvatarFileChange}
          />
        )}
        {canEditAvatar && user?.avatar && (
          <button className="profile-remove-photo" onClick={() => onAvatarChange?.(null)}>
            Убрать фото
          </button>
        )}

        {isOwnProfile ? (
          <>
            <input
              className="profile-name-input"
              value={editName}
              onChange={e => { setEditName(e.target.value); setDirty(true); setSaved(false) }}
              placeholder="Ваше имя"
            />
            <button
              className="profile-save-btn"
              disabled={!dirty}
              onClick={handleSaveOwnName}
            >
              Сохранить
            </button>
            {saved && <span className="profile-saved-msg">Сохранено ✓</span>}
          </>
        ) : (
          <>
            <h2 className="profile-name">{user?.name ?? 'Пользователь'}</h2>
          </>
        )}

        <p className="profile-username">@{user?.username ?? 'username'}</p>

        <div className="profile-status">
          {user?.online ? '🟢 В сети' : '⚫ Не в сети'}
        </div>

        <div className="profile-info">
          <div className="profile-row">
            <span className="profile-label">Email</span>
            <span className="profile-value">{user?.email ?? '—'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">ID</span>
            <span className="profile-value">#{user?.id ?? '0'}</span>
          </div>
        </div>

        {!isOwnProfile && (
          <div className="profile-alias-section">
            <label>Локальное имя</label>
            <input
              className="profile-alias-input"
              value={editName}
              onChange={e => { setEditName(e.target.value); setDirty(true); setSaved(false) }}
              placeholder={user?.name ?? 'Псевдоним'}
            />
            <p className="profile-alias-hint">Отображается только у вас</p>
            <button
              className="profile-save-btn"
              onClick={handleSaveAlias}
            >
              Сохранить
            </button>
            {saved && <span className="profile-saved-msg">Сохранено ✓</span>}
          </div>
        )}

        {!isOwnProfile && (
          <button className="profile-msg-btn">Написать сообщение</button>
        )}
      </div>
    </div>
  )
}

function CameraIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
      <circle cx="12" cy="13" r="4" />
    </svg>
  )
}
