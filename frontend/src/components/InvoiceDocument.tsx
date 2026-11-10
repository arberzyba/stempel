import { useEffect, useRef, type ReactNode } from 'react'
import type { InvoiceArea, InvoiceView, Party, Severity } from '../api/types'
import {
  formatDate,
  formatIban,
  formatMoney,
  formatNumber,
  formatPercent,
  unitLabel,
  vatCategoryLabel,
} from '../format'
import './InvoiceDocument.css'

export interface Highlight {
  area: InvoiceArea
  severity: Severity
}

interface InvoiceDocumentProps {
  invoice: InvoiceView
  /** Placed in the top right corner, like a real stamp on paper. */
  stamp?: ReactNode
  /** The part of the invoice a selected finding is about. */
  highlight?: Highlight | null
}

/** Shows an invoice laid out like a German business letter (DIN 5008). */
export function InvoiceDocument({ invoice, stamp, highlight }: InvoiceDocumentProps) {
  const { seller, buyer, totals, payment } = invoice
  const money = (amount: number | null | undefined) => formatMoney(amount, invoice.currency)
  const articleRef = useRef<HTMLElement>(null)

  // Scroll the highlighted part into view, e.g. on a phone where the panel is below the invoice.
  // Depends on the area string, not the object, so it doesn't re-run on every render.
  const highlightedArea = highlight?.area
  useEffect(() => {
    if (!highlightedArea) return
    const element = articleRef.current?.querySelector(`[data-area="${highlightedArea}"]`)
    element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  }, [highlightedArea])

  // Adds the highlight class to the part that matches the selected finding.
  const area = (name: InvoiceArea, className: string) => ({
    'data-area': name,
    className: highlight?.area === name
      ? `${className} doc__highlight doc__highlight--${highlight.severity.toLowerCase()}`
      : className,
  })

  return (
    <article className="doc" aria-label="Rechnung" ref={articleRef}>
      {stamp}
      <div {...area('seller', 'doc__letterhead')}>
        <span className="doc__seller">{seller.name ?? 'Unbekannter Absender'}</span>
        <span className="doc__seller-address">{addressLine(seller)}</span>
      </div>

      <div className="doc__head">
        <address {...area('buyer', 'doc__recipient')}>
          <span className="doc__return-line">{[seller.name, addressLine(seller)].filter(Boolean).join(' · ')}</span>
          <span className="doc__recipient-name">{buyer.name ?? '–'}</span>
          {buyer.street && <span>{buyer.street}</span>}
          {(buyer.zip || buyer.city) && <span>{[buyer.zip, buyer.city].filter(Boolean).join(' ')}</span>}
          {buyer.country && <span>{buyer.country}</span>}
        </address>

        <dl className="doc__meta">
          <dt>Rechnungsnr.</dt><dd className="mono">{invoice.number ?? '–'}</dd>
          <dt>Datum</dt><dd className="mono">{formatDate(invoice.issueDate)}</dd>
          {invoice.dueDate && (<><dt>Fällig am</dt><dd className="mono">{formatDate(invoice.dueDate)}</dd></>)}
          <dt>Käuferreferenz</dt><dd className="mono">{invoice.buyerReference ?? '–'}</dd>
          {seller.vatId && (<><dt>USt-IdNr.</dt><dd className="mono">{seller.vatId}</dd></>)}
        </dl>
      </div>

      <h1 className="doc__title">Rechnung</h1>

      <div {...area('lines', 'doc__table-wrap')}>
        <table className="doc__table">
          <thead>
            <tr>
              <th scope="col">Pos.</th>
              <th scope="col">Bezeichnung</th>
              <th scope="col" className="num">Menge</th>
              <th scope="col" className="num">Preis</th>
              <th scope="col" className="num">USt.</th>
              <th scope="col" className="num">Netto</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lines.map((line, index) => (
              // Lines have no stable id in the data, and their order never changes, so the index is a safe key.
              <tr key={index}>
                <td className="mono muted">{index + 1}</td>
                <td>
                  {line.name ?? '–'}
                  {line.description && line.description !== line.name && (
                    <span className="doc__line-description">{line.description}</span>
                  )}
                </td>
                <td className="num mono">{formatNumber(line.quantity)} {unitLabel(line.unit)}</td>
                <td className="num mono">{money(line.unitPrice)}</td>
                <td className="num mono">{formatPercent(line.vatPercent)}</td>
                <td className="num mono">{money(line.netAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div {...area('totals', 'doc__totals')}>
        <div className="doc__totals-row">
          <span className="muted">Summe netto</span>
          <span className="mono">{money(totals.net)}</span>
        </div>
        {invoice.vat.map((vat) => (
          <div className="doc__totals-row" key={`${vat.category}-${vat.percent}`}>
            <span className="muted">
              USt. {formatPercent(vat.percent)} auf {money(vat.basis)}
              {vatCategoryLabel(vat.category) && ` (${vatCategoryLabel(vat.category)})`}
            </span>
            <span className="mono">{money(vat.amount)}</span>
          </div>
        ))}
        <div className="doc__totals-row doc__totals-row--grand">
          <span>Gesamt</span>
          <span className="mono">{money(totals.gross)}</span>
        </div>
        {totals.duePayable !== totals.gross && (
          <div className="doc__totals-row">
            <span>Zu zahlen</span>
            <span className="mono">{money(totals.duePayable)}</span>
          </div>
        )}
      </div>

      <footer {...area('payment', 'doc__payment')}>
        {payment.terms && <span>{payment.terms}</span>}
        <span className="muted">IBAN: <span className="mono">{payment.iban ? formatIban(payment.iban) : 'nicht angegeben'}</span></span>
      </footer>
    </article>
  )
}

function addressLine(party: Party): string {
  const place = [party.zip, party.city].filter(Boolean).join(' ')
  return [party.street, place, party.country].filter(Boolean).join(' · ')
}
