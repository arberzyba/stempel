import type { InvoiceView } from '../api/types'
import './CheckPanel.css'

interface CheckPanelProps {
  invoice: InvoiceView
  fileName: string
}

/**
 * Side panel next to the invoice. For now it shows what was read; the
 * validation against EN 16931 (the "Prüfbericht") is added in week 2.
 */
export function CheckPanel({ invoice, fileName }: CheckPanelProps) {
  return (
    <aside className="check" aria-label="Prüfbericht">
      <section className="check__card">
        <div className="check__head">
          <h2 className="check__title">Prüfbericht</h2>
          <span className="check__badge mono">EN 16931</span>
        </div>
        <p className="check__status">Rechnung gelesen</p>
        <p className="check__text">
          Die Prüfung nach den offiziellen Regeln folgt in Kürze. Bis dahin zeigt Stempel
          den Inhalt der Rechnung an.
        </p>
        <dl className="check__facts">
          <dt>Format</dt><dd className="mono">{formatOf(fileName)}</dd>
          <dt>Positionen</dt><dd className="mono">{invoice.lines.length}</dd>
          <dt>Währung</dt><dd className="mono">{invoice.currency ?? '–'}</dd>
        </dl>
      </section>
      <p className="check__note muted">
        Summen und Steuern hat Stempel aus den Positionen nachgerechnet.
      </p>
    </aside>
  )
}

function formatOf(fileName: string): string {
  return fileName.toLowerCase().endsWith('.pdf') ? 'ZUGFeRD (PDF)' : 'XRechnung (XML)'
}
