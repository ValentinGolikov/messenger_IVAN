import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import '../styles/callback.css'

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState('loading') // loading | success | error
  const [errorMsg, setErrorMsg] = useState('')
  const navigate = useNavigate()
  const { saveUser } = useAuth()

  useEffect(() => {
    const code = searchParams.get('code')
    const error = searchParams.get('error')

    if (error) {
      setStatus('error')
      setErrorMsg(`Яндекс вернул ошибку: ${error}`)
      return
    }

    if (!code) {
      setStatus('error')
      setErrorMsg('Код авторизации не получен')
      return
    }

    exchangeCode(code)
  }, [])

  async function exchangeCode(code) {
    try {
      const res = await fetch(`${API_URL}/api/auth/yandex/callback`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
      })

      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.message || `HTTP ${res.status}`)
      }

      const data = await res.json()
      // Ожидаемый формат: { token: string, user: { id, name, email, avatar } }
      localStorage.setItem('token', data.token)
      saveUser(data.user)

      setStatus('success')
      setTimeout(() => navigate('/'), 1000)
    } catch (err) {
      setStatus('error')
      setErrorMsg(err.message)
    }
  }

  return (
    <div className="callback-page">
      <div className="callback-card">
        {status === 'loading' && (
          <>
            <div className="spinner" />
            <p>Выполняем вход...</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className="callback-icon success">✓</div>
            <p>Успешно! Перенаправляем...</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="callback-icon error">✕</div>
            <p className="callback-error">{errorMsg}</p>
            <button className="retry-btn" onClick={() => navigate('/login')}>
              Вернуться к входу
            </button>
          </>
        )}
      </div>
    </div>
  )
}
