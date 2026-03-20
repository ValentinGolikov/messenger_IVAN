import { useRef } from 'react'
import '../styles/settings.css'

const ZOOM_STEPS = [75, 90, 100, 110, 125, 150]
const FONT_SIZES = [
  { value: 'small',  label: 'Мелкий' },
  { value: 'medium', label: 'Средний' },
  { value: 'large',  label: 'Крупный' },
]

export default function SettingsPanel({ settings, onUpdate, theme, onToggleTheme, onClose, appearance, onUpdateAppearance, onResetAppearance }) {
  const imgInputRef = useRef(null)

  function handleBgImageUpload(e) {
    const file = e.target.files[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => onUpdateAppearance('chatBgImage', ev.target.result)
    reader.readAsDataURL(file)
    e.target.value = ''
  }
  return (
    <div className="settings-overlay" onClick={onClose}>
      <div className="settings-panel" onClick={e => e.stopPropagation()}>
        <div className="settings-header">
          <h2>Настройки</h2>
          <button className="settings-close" onClick={onClose}>✕</button>
        </div>

        <div className="settings-body">

          {/* ── Внешний вид ── */}
          <Section title="Внешний вид">
            <Row label="Тема оформления">
              <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            </Row>

            <Row label={`Масштаб: ${settings.zoom}%`}>
              <div className="zoom-controls">
                <button
                  className="zoom-btn"
                  disabled={settings.zoom <= ZOOM_STEPS[0]}
                  onClick={() => {
                    const i = ZOOM_STEPS.indexOf(settings.zoom)
                    if (i > 0) onUpdate('zoom', ZOOM_STEPS[i - 1])
                  }}
                >−</button>
                <div className="zoom-track">
                  {ZOOM_STEPS.map(z => (
                    <button
                      key={z}
                      className={`zoom-step ${settings.zoom === z ? 'active' : ''}`}
                      onClick={() => onUpdate('zoom', z)}
                      title={`${z}%`}
                    />
                  ))}
                </div>
                <button
                  className="zoom-btn"
                  disabled={settings.zoom >= ZOOM_STEPS[ZOOM_STEPS.length - 1]}
                  onClick={() => {
                    const i = ZOOM_STEPS.indexOf(settings.zoom)
                    if (i < ZOOM_STEPS.length - 1) onUpdate('zoom', ZOOM_STEPS[i + 1])
                  }}
                >+</button>
              </div>
            </Row>

            <Row label="Размер шрифта">
              <div className="seg-control">
                {FONT_SIZES.map(f => (
                  <button
                    key={f.value}
                    className={settings.fontSize === f.value ? 'active' : ''}
                    onClick={() => onUpdate('fontSize', f.value)}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            </Row>
          </Section>

          {/* ── Фон чата ── */}
          <Section title="Фон чата">
            <Row label="Цвет фона">
              <div className="settings-row-controls">
                <input
                  type="color"
                  className="settings-color-picker"
                  value={appearance?.chatBg || '#ffffff'}
                  onChange={e => onUpdateAppearance('chatBg', e.target.value)}
                />
                <button className="settings-reset-link" onClick={() => onUpdateAppearance('chatBg', '')}>Сброс</button>
              </div>
            </Row>
            <Row label="Фоновое изображение">
              <div className="settings-row-controls">
                <button className="settings-img-upload-btn" onClick={() => imgInputRef.current?.click()}>
                  Выбрать файл
                </button>
                {appearance?.chatBgImage && (
                  <button className="settings-reset-link" onClick={() => onUpdateAppearance('chatBgImage', '')}>Убрать</button>
                )}
                <input
                  ref={imgInputRef}
                  type="file"
                  accept="image/*"
                  style={{ display: 'none' }}
                  onChange={handleBgImageUpload}
                />
              </div>
            </Row>
          </Section>

          {/* ── Цвет сообщений ── */}
          <Section title="Цвет сообщений">
            <Row label="Мои (фон)">
              <div className="settings-row-controls">
                <input
                  type="color"
                  className="settings-color-picker"
                  value={appearance?.msgMeBg || '#3b82f6'}
                  onChange={e => onUpdateAppearance('msgMeBg', e.target.value)}
                />
                <button className="settings-reset-link" onClick={() => onUpdateAppearance('msgMeBg', '')}>Сброс</button>
              </div>
            </Row>
            <Row label="Мои (текст)">
              <div className="settings-row-controls">
                <input
                  type="color"
                  className="settings-color-picker"
                  value={appearance?.msgMeText || '#ffffff'}
                  onChange={e => onUpdateAppearance('msgMeText', e.target.value)}
                />
                <button className="settings-reset-link" onClick={() => onUpdateAppearance('msgMeText', '')}>Сброс</button>
              </div>
            </Row>
            <Row label="Собеседник (фон)">
              <div className="settings-row-controls">
                <input
                  type="color"
                  className="settings-color-picker"
                  value={appearance?.msgThemBg || '#f3f4f6'}
                  onChange={e => onUpdateAppearance('msgThemBg', e.target.value)}
                />
                <button className="settings-reset-link" onClick={() => onUpdateAppearance('msgThemBg', '')}>Сброс</button>
              </div>
            </Row>
            <Row label="Собеседник (текст)">
              <div className="settings-row-controls">
                <input
                  type="color"
                  className="settings-color-picker"
                  value={appearance?.msgThemText || '#111827'}
                  onChange={e => onUpdateAppearance('msgThemText', e.target.value)}
                />
                <button className="settings-reset-link" onClick={() => onUpdateAppearance('msgThemText', '')}>Сброс</button>
              </div>
            </Row>
            <button className="settings-reset-btn" onClick={onResetAppearance}>Сбросить всё</button>
          </Section>

          {/* ── Сообщения ── */}
          <Section title="Сообщения">
            <Row label="Отправка по Enter">
              <Toggle
                value={settings.sendOnEnter}
                onChange={v => onUpdate('sendOnEnter', v)}
              />
            </Row>
            <Row label="Уведомления">
              <Toggle
                value={settings.notifications}
                onChange={v => onUpdate('notifications', v)}
              />
            </Row>
          </Section>

          {/* ── Прокси ── */}
          <Section title="Прокси">
            <Row label="Использовать прокси">
              <Toggle
                value={settings.proxyEnabled}
                onChange={v => onUpdate('proxyEnabled', v)}
              />
            </Row>
            {settings.proxyEnabled && (
              <>
                <Row label="Тип">
                  <select
                    className="settings-select"
                    value={settings.proxyType}
                    onChange={e => onUpdate('proxyType', e.target.value)}
                  >
                    <option value="socks5">SOCKS5</option>
                    <option value="socks4">SOCKS4</option>
                    <option value="http">HTTP</option>
                    <option value="https">HTTPS</option>
                  </select>
                </Row>
                <Row label="Хост">
                  <input
                    className="settings-input"
                    type="text"
                    placeholder="127.0.0.1"
                    value={settings.proxyHost}
                    onChange={e => onUpdate('proxyHost', e.target.value)}
                  />
                </Row>
                <Row label="Порт">
                  <input
                    className="settings-input"
                    type="text"
                    placeholder="1080"
                    value={settings.proxyPort}
                    onChange={e => onUpdate('proxyPort', e.target.value)}
                  />
                </Row>
              </>
            )}
          </Section>

          {/* ── Прочее ── */}
          <Section title="Прочее">
            <Row label="Язык интерфейса">
              <select
                className="settings-select"
                value={settings.language}
                onChange={e => onUpdate('language', e.target.value)}
              >
                <option value="ru">Русский</option>
                <option value="en">English</option>
              </select>
            </Row>
          </Section>

          <div className="settings-version">Polytech Messenger v0.1.0 — dev build</div>
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }) {
  return (
    <div className="settings-section">
      <p className="settings-section-title">{title}</p>
      {children}
    </div>
  )
}

function Row({ label, children }) {
  return (
    <div className="settings-row">
      <span className="settings-row-label">{label}</span>
      <div className="settings-row-control">{children}</div>
    </div>
  )
}

function Toggle({ value, onChange }) {
  return (
    <button
      className={`toggle ${value ? 'on' : ''}`}
      onClick={() => onChange(!value)}
      role="switch"
      aria-checked={value}
    >
      <span className="toggle-thumb" />
    </button>
  )
}

function ThemeToggle({ theme, onToggle }) {
  return (
    <button className="theme-seg" onClick={onToggle}>
      <span className={theme === 'light' ? 'active' : ''}>☀️ Светлая</span>
      <span className={theme === 'dark' ? 'active' : ''}>🌙 Тёмная</span>
    </button>
  )
}
