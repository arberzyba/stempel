import type { ApiError } from './api/client'
import type { Finding, Severity, ValidationReport } from './api/types'

/** The state of the check while the invoice is already shown. */
export type Validation =
  | { status: 'checking' }
  | { status: 'done'; report: ValidationReport; checkedAt: Date }
  | { status: 'failed'; error: ApiError }

export function findingsOf(validation: Validation, severity: Severity): Finding[] {
  return validation.status === 'done'
    ? validation.report.findings.filter((finding) => finding.severity === severity)
    : []
}

/** 1 → "1 Fehler", 2 → "2 Warnungen" */
export function countLabel(count: number, severity: Severity): string {
  const words: Record<Severity, [string, string]> = {
    ERROR: ['Fehler', 'Fehler'],
    WARNING: ['Warnung', 'Warnungen'],
    NOTICE: ['Hinweis', 'Hinweise'],
  }
  const [one, many] = words[severity]
  return `${count} ${count === 1 ? one : many}`
}
