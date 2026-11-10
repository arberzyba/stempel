import type { ApiError } from '../api/client'
import './ErrorMessage.css'

interface ErrorMessageProps {
  error: ApiError
  fileName: string
  onReset: () => void
}

export function ErrorMessage({ error, fileName, onReset }: ErrorMessageProps) {
  const code = error.status > 0 ? `FEHLER ${error.status}` : 'FEHLER'
  return (
    <main className="page error">
      <div className="error__box" role="alert">
        <span className="error__code mono">{code} · {fileName.toUpperCase()}</span>
        <h1 className="error__title">{error.title}</h1>
        <p className="error__text">{error.message}</p>
        <div>
          <button type="button" className="button button--primary" onClick={onReset}>
            Andere Datei wählen
          </button>
        </div>
      </div>
    </main>
  )
}
