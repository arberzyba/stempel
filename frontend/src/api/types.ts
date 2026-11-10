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

export interface Totals {
  net: number
  vat: number
  gross: number
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

/** Error body from the backend (RFC 9457 problem details). */
export interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
}
