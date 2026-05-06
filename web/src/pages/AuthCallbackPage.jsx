import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { apiLogin } from '../lib/api'
import '../styles/callback.css'

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState('loading') // loading | success | error
  const [errorMsg, setErrorMsg] = useState('')
  const navigate = useNavigate()
  const { saveUser } = useAuth()

  useEffect(() => {
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const accessToken = hash.get('access_token')
    const error = searchParams.get('error')

    if (error) {
      setStatus('error')
      setErrorMsg(`Яндекс вернул ошибку: ${error}`)
      return
    }

    if (!accessToken) {
      setStatus('error')
      setErrorMsg('Токен авторизации не получен')
      return
    }

    exchangeToken(accessToken)
  }, [])

  async function exchangeToken(token) {
    try {
      const data = await apiLogin(token)
      const user = {
        id: data.userId,
        name: data.yandexData?.displayName || data.yandexData?.realName || 'Пользователь',
        displayName: data.yandexData?.displayName || null,
        email: data.yandexData?.email || null,
        username: data.yandexData?.login || null,
        phone: data.yandexData?.defaultPhone?.number || null,
        avatar: null,
      }
      localStorage.setItem('token', token)
      saveUser(user)

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
