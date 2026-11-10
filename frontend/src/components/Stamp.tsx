import type { ValidationReport } from '../api/types'
import { countLabel } from '../validation'
import './Stamp.css'

interface StampProps {
  report: ValidationReport
  /** When the check finished. Passed in, so rendering stays pure (no new Date() here). */
  checkedAt: Date
}

/** The "GEPRÜFT" / "NICHT GÜLTIG" stamp on the invoice. */
export function Stamp({ report, checkedAt }: StampProps) {
  const today = new Intl.DateTimeFormat('de-DE').format(checkedAt)
  const errors = report.findings.filter((finding) => finding.severity === 'ERROR').length

  if (report.valid) {
    const rules = report.profile ?? 'EN 16931'
    return (
      <div className="stamp stamp--ok" role="img" aria-label={`Geprüft nach ${rules} am ${today}`}>
        <span className="stamp__main">GEPRÜFT</span>
        <span className="stamp__sub">{rules.toUpperCase()} · {today}</span>
      </div>
    )
  }
  return (
    <div className="stamp stamp--error" role="img" aria-label={`Nicht gültig, ${countLabel(errors, 'ERROR')}`}>
      <span className="stamp__main">NICHT GÜLTIG</span>
      <span className="stamp__sub">{countLabel(errors, 'ERROR').toUpperCase()} · {today}</span>
    </div>
  )
}
