import { useState } from 'react'
import type { InvoiceView } from '../api/types'
import type { Validation } from '../validation'
import { CheckPanel } from './CheckPanel'
import { InvoiceDocument } from './InvoiceDocument'
import { Stamp } from './Stamp'

interface ViewerProps {
  invoice: InvoiceView
  fileName: string
  validation: Validation
}

/** The invoice and its check report side by side. */
export function Viewer({ invoice, fileName, validation }: ViewerProps) {
  // Which finding is selected; its area gets highlighted in the invoice.
  const [selected, setSelected] = useState<number | null>(null)

  const done = validation.status === 'done' ? validation : null
  const finding = done && selected !== null ? done.report.findings[selected] : undefined
  const highlight = finding?.area ? { area: finding.area, severity: finding.severity } : null

  return (
    <main className="page viewer">
      <InvoiceDocument
        invoice={invoice}
        stamp={done && <Stamp report={done.report} checkedAt={done.checkedAt} />}
        highlight={highlight}
      />
      <CheckPanel
        validation={validation}
        fileName={fileName}
        selected={selected}
        onSelect={setSelected}
      />
    </main>
  )
}
