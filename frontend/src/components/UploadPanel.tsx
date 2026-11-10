import { useRef, useState, type DragEvent } from 'react'
import './UploadPanel.css'

interface UploadPanelProps {
  onFile: (file: File) => void
  busy: boolean
}

export function UploadPanel({ onFile, busy }: UploadPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  function handleDrop(event: DragEvent) {
    event.preventDefault() // otherwise the browser opens the file itself
    setDragging(false)
    const file = event.dataTransfer.files[0]
    if (file && !busy) onFile(file)
  }

  return (
    <main className="page upload">
      <div className="upload__intro">
        <span className="upload__eyebrow mono">XRECHNUNG · ZUGFERD · EN 16931</span>
        <h1 className="upload__title">E-Rechnungen.<br />Geprüft. Fertig.</h1>
        <p className="upload__lead">
          Öffnen Sie eine E-Rechnung als lesbares Dokument und prüfen Sie sie gegen die
          offiziellen Regeln – direkt auf Ihrem Rechner.
        </p>
      </div>

      <div className="upload__panel">
        <div
          className={dragging ? 'upload__drop upload__drop--active' : 'upload__drop'}
          onDragOver={(event) => {
            event.preventDefault() // needed so the browser allows dropping here
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
        >
          <div className="upload__drop-text">
            <span className="upload__drop-title">
              {busy ? 'Rechnung wird gelesen …' : 'Datei hier ablegen'}
            </span>
            <span className="muted">oder vom Computer auswählen</span>
          </div>
          <div>
            <button
              type="button"
              className="button button--primary"
              onClick={() => inputRef.current?.click()}
              disabled={busy}
            >
              Datei auswählen
            </button>
          </div>
          <input
            ref={inputRef}
            type="file"
            className="visually-hidden"
            tabIndex={-1}
            aria-hidden="true"
            accept=".xml,.pdf,application/xml,text/xml,application/pdf"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) onFile(file)
              event.target.value = '' // so choosing the same file again still fires onChange
            }}
          />
        </div>

        <dl className="upload__facts">
          <dt>XRechnung</dt><dd className="mono">.xml · UBL oder CII</dd>
          <dt>ZUGFeRD</dt><dd className="mono">.pdf · ab Version 2</dd>
          <dt>Größe</dt><dd className="mono">max. 10 MB</dd>
          <dt>Speicherung</dt><dd className="mono">keine</dd>
        </dl>
      </div>

      <div className="upload__footer muted">
        <span>Läuft lokal mit Docker</span>
        <span>Open Source · Apache 2.0</span>
      </div>
    </main>
  )
}
