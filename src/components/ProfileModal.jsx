import { useEffect, useRef } from 'react'
import '../styles/profile.css'

export default function ProfileModal({ user, onClose }) {
  const ref = useRef(null)

  useEffect(() => {
    function handleKey(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [onClose])

  return (
    <div className="profile-overlay" onClick={onClose}>
      <div className="profile-modal" ref={ref} onClick={e => e.stopPropagation()}>
        <button className="profile-close" onClick={onClose}>✕</button>

        <div className="profile-avatar-big">
          {user?.avatar
            ? <img src={user.avatar} alt={user.name} />
            : <span>{user?.name?.[0] ?? '?'}</span>
          }
          <span className={`profile-online-dot ${user?.online ? 'online' : ''}`} />
        </div>

        <h2 className="profile-name">{user?.name ?? 'Пользователь'}</h2>
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

        <button className="profile-msg-btn">Написать сообщение</button>
      </div>
    </div>
  )
}
