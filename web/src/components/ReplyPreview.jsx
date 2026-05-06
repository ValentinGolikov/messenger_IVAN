import '../styles/reply.css'

export default function ReplyPreview({ replyTo, onClose, onClick }) {
  return (
    <div className="reply-preview">
      <div className="reply-bar" />
      <div className="reply-content" onClick={onClick}>
        <span className="reply-sender">
          {replyTo.from === 'me' ? 'Вы' : 'Собеседник'}
        </span>
        <span className="reply-text">
          {replyTo.file ? `📎 ${replyTo.file.name}` : replyTo.text}
        </span>
        <span className="reply-hint">Нажмите, чтобы переслать как цитату</span>
      </div>
      <button className="reply-close" onClick={onClose} title="Отмена">✕</button>
    </div>
  )
}
