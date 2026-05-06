import { useEffect, useRef } from 'react'
import '../styles/context-menu.css'

/**
 * items: Array<{ label, icon, action, danger, divider }>
 * position: { x, y }
 * onClose: () => void
 */
export default function ContextMenu({ items, position, onClose }) {
  const ref = useRef(null)

  useEffect(() => {
    function handleDown(e) {
      if (ref.current && !ref.current.contains(e.target)) onClose()
    }
    function handleKey(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('mousedown', handleDown)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handleDown)
      document.removeEventListener('keydown', handleKey)
    }
  }, [onClose])

  // Не даём меню выйти за правый/нижний край экрана
  const MENU_W = 200
  const MENU_APPROX_H = items.length * 36
  const x = Math.min(position.x, window.innerWidth - MENU_W - 8)
  const y = Math.min(position.y, window.innerHeight - MENU_APPROX_H - 8)

  return (
    <div
      ref={ref}
      className="ctx-menu"
      style={{ top: y, left: x }}
    >
      {items.map((item, i) =>
        item.divider
          ? <div key={i} className="ctx-divider" />
          : (
            <button
              key={i}
              className={`ctx-item ${item.danger ? 'danger' : ''}`}
              onClick={() => { item.action(); onClose() }}
            >
              {item.icon && <span className="ctx-icon">{item.icon}</span>}
              {item.label}
            </button>
          )
      )}
    </div>
  )
}
