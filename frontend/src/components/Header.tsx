import './Header.css'

interface HeaderProps {
  /** Shown next to the logo once a file is open. */
  fileName?: string
  /** If set, a "Neue Datei" button is shown. */
  onReset?: () => void
}

export function Header({ fileName, onReset }: HeaderProps) {
  return (
    <header className="header">
      <div className="page header__inner">
        <div className="header__brand">
          <StampIcon />
          <span className="header__name">Stempel</span>
          {fileName && (
            <>
              <span className="header__slash" aria-hidden="true">/</span>
              <span className="header__file mono" title={fileName}>{fileName}</span>
            </>
          )}
        </div>
        <div className="header__actions">
          <span className="header__tag mono">LOKAL · KEIN KONTO</span>
          {onReset && (
            <button type="button" className="button button--secondary" onClick={onReset}>
              Neue Datei
            </button>
          )}
        </div>
      </div>
    </header>
  )
}

function StampIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 3h6v5l-1.5 2h-3L9 8z" />
      <path d="M4 14h16v4H4z" />
      <path d="M6 21h12" />
    </svg>
  )
}
