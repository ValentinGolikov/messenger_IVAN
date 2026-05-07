import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { apiPasswordLogin, apiPasswordRegister } from '../lib/api'
import '../styles/login.css'

const YANDEX_CLIENT_ID = import.meta.env.VITE_YANDEX_CLIENT_ID
const REDIRECT_URI = import.meta.env.VITE_REDIRECT_URI

function buildYandexOAuthUrl() {
  const params = new URLSearchParams({
    response_type: 'token',
    client_id: YANDEX_CLIENT_ID,
    redirect_uri: REDIRECT_URI,
  })
  return `https://oauth.yandex.ru/authorize?${params}`
}

export default function LoginPage() {
  const { saveUser } = useAuth()
  const navigate = useNavigate()
  const [authMode, setAuthMode] = useState('login')
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function handleYandexLogin() {
    if (!YANDEX_CLIENT_ID) {
      alert('VITE_YANDEX_CLIENT_ID не задан в .env')
      return
    }
    window.location.href = buildYandexOAuthUrl()
  }

  function handleDevLogin() {
    saveUser({ id: 0, name: 'Dev User', email: 'dev@test.local', avatar: null })
    navigate('/')
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault()
    setError('')

    if (login.trim().length < 3 || password.length < 6) {
      setError('Логин от 3 символов, пароль от 6 символов')
      return
    }

    try {
      setLoading(true)
      const data = authMode === 'login'
        ? await apiPasswordLogin(login.trim(), password)
        : await apiPasswordRegister(login.trim(), password, displayName.trim())

      localStorage.setItem('token', data.token)
      saveUser({
        id: data.user.id,
        name: data.user.name,
        displayName: data.user.name,
        email: data.user.email || null,
        username: data.user.username,
        avatar: null,
      })
      navigate('/')
    } catch (err) {
      setError(err.message || 'Не удалось войти')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect width="48" height="48" rx="12" fill="#2563eb" />
            <path
              d="M14 34V14h10c2.5 0 4.5.7 6 2.1 1.5 1.4 2.2 3.2 2.2 5.4 0 1.5-.4 2.8-1.1 4-.7 1.1-1.7 2-3 2.6L34 34h-5.5l-5.2-9.2H18V34h-4zm4-13h5.8c1.2 0 2.1-.3 2.8-1 .7-.7 1-1.5 1-2.6 0-1-.3-1.9-1-2.5-.7-.6-1.6-1-2.8-1H18v7.1z"
              fill="white"
            />
          </svg>
        </div>

        <h1 className="login-title">файлообменник ИВАН</h1>
        <p className="login-subtitle">Войдите, чтобы продолжить</p>

        <form className="password-login-form" onSubmit={handlePasswordSubmit}>
          <div className="login-mode-switch" role="tablist" aria-label="Режим входа">
            <button
              type="button"
              className={authMode === 'login' ? 'active' : ''}
              onClick={() => { setAuthMode('login'); setError('') }}
            >
              Вход
            </button>
            <button
              type="button"
              className={authMode === 'register' ? 'active' : ''}
              onClick={() => { setAuthMode('register'); setError('') }}
            >
              Регистрация
            </button>
          </div>

          {authMode === 'register' && (
            <input
              className="login-input"
              type="text"
              placeholder="Имя"
              value={displayName}
              onChange={e => setDisplayName(e.target.value)}
              autoComplete="name"
            />
          )}

          <input
            className="login-input"
            type="text"
            placeholder="Логин"
            value={login}
            onChange={e => setLogin(e.target.value)}
            autoComplete="username"
          />

          <input
            className="login-input"
            type="password"
            placeholder="Пароль"
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
          />

          {error && <p className="login-error">{error}</p>}

          <button className="password-login-btn" type="submit" disabled={loading}>
            {loading ? 'Проверяем...' : (authMode === 'login' ? 'Войти' : 'Создать аккаунт')}
          </button>
        </form>

        <div className="login-divider"><span>или</span></div>

        <button className="yandex-btn" onClick={handleYandexLogin}>
          <YandexIcon />
          Войти через Яндекс
        </button>

        <p className="login-hint">
          Нет аккаунта? Он будет создан автоматически при первом входе.
        </p>

        <button className="dev-btn" onClick={handleDevLogin}>
          Войти как гость (dev)
        </button>
      </div>
    </div>
  )
}

function YandexIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M12.068 2C6.478 2 2 6.477 2 12.068c0 5.59 4.478 10.068 10.068 10.068 5.59 0 10.068-4.478 10.068-10.068C22.136 6.477 17.658 2 12.068 2zm1.386 15.97h-2.07v-7.61l-3.213 7.61H6.1L9.77 10.27H6.97V8.03h6.484v9.94z"
        fill="#FC3F1D"
      />
    </svg>
  )
}
