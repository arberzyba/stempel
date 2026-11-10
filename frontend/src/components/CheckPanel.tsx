import type { Finding, ValidationReport } from '../api/types'
import { countLabel, findingsOf, type Validation } from '../validation'
import './CheckPanel.css'

interface CheckPanelProps {
  validation: Validation
  fileName: string
  /** Index of the selected finding in report.findings, or null. */
  selected: number | null
  onSelect: (index: number | null) => void
}

/** The "Prüfbericht" next to the invoice. */
export function CheckPanel({ validation, fileName, selected, onSelect }: CheckPanelProps) {
  return (
    <aside className="check" aria-label="Prüfbericht">
      <section className="check__card" aria-live="polite">
        <div className="check__head">
          <h2 className="check__title">Prüfbericht</h2>
          <span className="check__badge mono">EN 16931</span>
        </div>
        <Summary validation={validation} fileName={fileName} />
      </section>

      {validation.status === 'done' && (
        <Findings report={validation.report} selected={selected} onSelect={onSelect} />
      )}

      <p className="check__note muted">
        Die Summen in der Ansicht hat Stempel aus den Positionen nachgerechnet.
      </p>
    </aside>
  )
}

function Summary({ validation, fileName }: { validation: Validation; fileName: string }) {
  switch (validation.status) {
    case 'checking':
      return (
        <>
          <p className="check__status check__status--checking">Wird geprüft …</p>
          <p className="check__text">Stempel prüft die Rechnung gegen die offiziellen Regeln.</p>
          <div className="check__progress" aria-hidden="true" />
        </>
      )
    case 'failed':
      return (
        <>
          <p className="check__status">Prüfung nicht möglich</p>
          <p className="check__text">{validation.error.message}</p>
        </>
      )
    case 'done': {
      const { report } = validation
      const errors = findingsOf(validation, 'ERROR').length
      const warnings = findingsOf(validation, 'WARNING').length
      return (
        <>
          {report.valid ? (
            <p className="check__status check__status--ok"><StatusIcon ok />Gültig</p>
          ) : (
            <p className="check__status check__status--error"><StatusIcon ok={false} />Nicht gültig</p>
          )}
          <p className="check__text">
            {report.valid
              ? warnings > 0
                ? `Die Rechnung erfüllt alle Pflichtregeln. ${countLabel(warnings, 'WARNING')} sollten Sie sich trotzdem ansehen.`
                : 'Die Rechnung erfüllt alle geprüften Regeln. Sie können sie annehmen und verbuchen.'
              : `${countLabel(errors, 'ERROR')} gefunden. Bitten Sie den Absender um eine korrigierte Rechnung.`}
          </p>
          <dl className="check__facts">
            <dt>Format</dt><dd className="mono">{formatLabel(report, fileName)}</dd>
            <dt>Regelwerk</dt><dd className="mono">{report.profile ?? '–'}</dd>
            {report.pdfChecked && (<><dt>PDF/A-3</dt><dd className="mono">geprüft</dd></>)}
          </dl>
        </>
      )
    }
  }
}

function Findings({ report, selected, onSelect }: {
  report: ValidationReport
  selected: number | null
  onSelect: (index: number | null) => void
}) {
  // Keep each finding's index in the full list, so selection works across the groups.
  const indexed = report.findings.map((finding, index) => ({ finding, index }))
  const errors = indexed.filter(({ finding }) => finding.severity === 'ERROR')
  const warnings = indexed.filter(({ finding }) => finding.severity === 'WARNING')
  const notices = indexed.filter(({ finding }) => finding.severity === 'NOTICE')

  const card = ({ finding, index }: { finding: Finding; index: number }) => (
    <FindingCard
      key={index}
      finding={finding}
      selected={selected === index}
      onSelect={() => onSelect(selected === index ? null : index)}
    />
  )

  return (
    <>
      {report.valid && errors.length === 0 && warnings.length === 0 && (
        <section className="check__card check__card--list">
          <h3 className="check__subtitle">Was geprüft wurde</h3>
          <ul className="check__done">
            <li><CheckIcon />Aufbau und Pflichtangaben</li>
            <li><CheckIcon />Rechenregeln der EN 16931</li>
            {report.profile?.startsWith('XRechnung') && <li><CheckIcon />Regeln der {report.profile}</li>}
            {report.pdfChecked && <li><CheckIcon />PDF/A-3 für die Archivierung</li>}
          </ul>
        </section>
      )}

      {errors.map(card)}
      {warnings.map(card)}

      {notices.length > 0 && (
        // Notices are hints, not problems, so they start collapsed.
        <details className="check__notices">
          <summary>{countLabel(notices.length, 'NOTICE')}</summary>
          <div className="check__notice-list">{notices.map(card)}</div>
        </details>
      )}
    </>
  )
}

const SEVERITY_LABEL = { ERROR: 'Fehler', WARNING: 'Warnung', NOTICE: 'Hinweis' } as const

function FindingCard({ finding, selected, onSelect }: {
  finding: Finding
  selected: boolean
  onSelect: () => void
}) {
  const severity = finding.severity.toLowerCase()
  const content = (
    <>
      <span className="finding__meta">
        <span className={`finding__dot finding__dot--${severity}`} aria-hidden="true" />
        <span className="mono">{[finding.ruleId, SEVERITY_LABEL[finding.severity]].filter(Boolean).join(' · ')}</span>
      </span>
      <span className="finding__title">{finding.title}</span>
      {finding.explanation && <span className="finding__text">{finding.explanation}</span>}
    </>
  )

  return (
    <section className={selected ? 'finding finding--selected' : 'finding'}>
      {finding.area ? (
        // Only findings we can point to in the invoice are clickable.
        <button type="button" className="finding__button" onClick={onSelect} aria-pressed={selected}>
          {content}
          <span className="finding__link">{selected ? 'Markierung entfernen' : 'Im Dokument zeigen'}</span>
        </button>
      ) : (
        <div className="finding__button">{content}</div>
      )}
      {finding.technicalMessage && finding.technicalMessage !== finding.title && (
        <details className="finding__details">
          <summary>Technische Meldung</summary>
          <p className="mono">{finding.technicalMessage}</p>
        </details>
      )}
    </section>
  )
}

function formatLabel(report: ValidationReport, fileName: string): string {
  if (report.pdfChecked) return report.format ? `ZUGFeRD · ${report.format}` : 'ZUGFeRD (PDF)'
  if (report.format) return `XML · ${report.format}`
  return fileName.toLowerCase().endsWith('.pdf') ? 'PDF' : 'XML'
}

function StatusIcon({ ok }: { ok: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" fill="none"
      stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      {ok ? <path d="M8 12.5l2.7 2.7L16 9.8" /> : <><path d="M9 9l6 6" /><path d="M15 9l-6 6" /></>}
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true" fill="none"
      stroke="var(--color-ok)" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  )
}
