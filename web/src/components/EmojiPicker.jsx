import { useEffect, useRef } from 'react'
import '../styles/emoji.css'

const EMOJIS = [
  '😀','😂','🥲','😊','😍','🤔','😅','😭','🥰','😎',
  '👋','👍','👎','❤️','🔥','✅','🎉','💯','🙏','⭐',
  '😡','😱','🤣','😴','🤗','😇','🥳','😈','💀','👀',
  '🐱','🐶','🍕','🍔','☕','🎮','📱','💻','📎','📁',
]

export default function EmojiPicker({ onSelect, onClose }) {
  const ref = useRef(null)

  useEffect(() => {
    function handleClick(e) {
      if (ref.current && !ref.current.contains(e.target)) onClose()
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [onClose])

  return (
    <div className="emoji-picker" ref={ref}>
      {EMOJIS.map(e => (
        <button
          key={e}
          className="emoji-btn"
          onClick={() => { onSelect(e); onClose() }}
        >
          {e}
        </button>
      ))}
    </div>
  )
}
