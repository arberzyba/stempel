// Mirrors the backend's InvoiceView record (backend/.../invoice/InvoiceView.java).
// Java's BigDecimal arrives as a JSON number. We only display amounts here and
// never calculate with them, so number is precise enough.
// Fields are "| null" because Java sends null when an invoice leaves a field out.

export interface Party {
  name: string | null
  street: string | null
  zip: string | null
  city: string | null
  country: string | null
  vatId: string | null
}

export interface Line {
  name: string | null
  description: string | null
  quantity: number | null
  unit: string | null
  unitPrice: number | null
  vatPercent: number | null
  netAmount: number | null
}

export interface VatLine {
  category: string | null
  percent: number | null
  basis: number | null
  amount: number | null
}

/** As stated in the invoice (not recalculated). */
export interface Totals {
  net: number
  vat: number
  gross: number
  /** Already paid, e.g. a down payment (BT-113); null if nothing was paid. */
  prepaid: number | null
  duePayable: number
}

export interface Payment {
  terms: string | null
  iban: string | null
}

export interface InvoiceView {
  number: string | null
  /** ISO date, e.g. "2024-11-15" */
  issueDate: string | null
  dueDate: string | null
  currency: string | null
  buyerReference: string | null
  seller: Party
  buyer: Party
  lines: Line[]
  vat: VatLine[]
  totals: Totals
  payment: Payment
}

// Mirrors backend/.../validation/ValidationReport.java

export type Severity = 'ERROR' | 'WARNING' | 'NOTICE'

/** Where in the invoice a finding belongs, so the document can highlight it. */
export type InvoiceArea = 'seller' | 'buyer' | 'lines' | 'totals' | 'payment'

export interface Finding {
  severity: Severity
  /** Official rule ID, e.g. "BR-CO-15"; may be empty for PDF checks. */
  ruleId: string
  title: string
  /** Plain German explanation, or null if Stempel has none for this rule. */
  explanation: string | null
  technicalMessage: string
  area: InvoiceArea | null
}

export interface ValidationReport {
  valid: boolean
  format: string | null
  profile: string | null
  pdfChecked: boolean
  /** Errors first, then warnings, then notices. */
  findings: Finding[]
}

/** Error body from the backend (RFC 9457 problem details). */
export interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
}
