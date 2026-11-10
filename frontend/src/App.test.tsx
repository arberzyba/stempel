import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { InvoiceView, ValidationReport } from './api/types'

// Shaped like the backend's real answers for the KoSIT sample 01.01a.
const sampleInvoice: InvoiceView = {
  number: '123456XX',
  issueDate: '2016-04-04',
  dueDate: null,
  currency: 'EUR',
  buyerReference: '04011000-12345-03',
  seller: { name: '[Seller name]', street: '[Seller address line 1]', zip: '12345', city: '[Seller city]', country: 'DE', vatId: 'DE 123456789' },
  buyer: { name: '[Buyer name]', street: '[Buyer address line 1]', zip: '12345', city: '[Buyer city]', country: 'DE', vatId: null },
  lines: [
    { name: 'Zeitschrift [...]', description: 'Zeitschrift Inland', quantity: 1, unit: 'XPP', unitPrice: 288.79, vatPercent: 7, netAmount: 288.79 },
    { name: 'Porto + Versandkosten', description: null, quantity: 1, unit: 'XPP', unitPrice: 26.07, vatPercent: 7, netAmount: 26.07 },
  ],
  vat: [{ category: 'S', percent: 7, basis: 314.86, amount: 22.04 }],
  totals: { net: 314.86, vat: 22.04, gross: 336.9, prepaid: null, duePayable: 336.9 },
  payment: { terms: 'Zahlbar sofort ohne Abzug.', iban: 'DE79000000001234567890' },
}

const validReport: ValidationReport = {
  valid: true,
  format: 'UBL',
  profile: 'XRechnung 3.0',
  pdfChecked: false,
  findings: [{
    severity: 'NOTICE',
    ruleId: 'BR-DE-TMP-32',
    title: 'Das Liefer- oder Leistungsdatum fehlt',
    explanation: 'Die Rechnung sollte angeben, wann geliefert oder geleistet wurde.',
    technicalMessage: 'Eine Rechnung sollte zur Angabe des Liefer-/Leistungsdatums ...',
    area: null,
  }],
}

const invalidReport: ValidationReport = {
  valid: false,
  format: 'CII',
  profile: 'XRechnung 3.0',
  pdfChecked: false,
  findings: [{
    severity: 'ERROR',
    ruleId: 'BR-CO-15',
    title: 'Der Gesamtbetrag stimmt nicht',
    explanation: 'Netto plus Umsatzsteuer ergibt nicht den angegebenen Gesamtbetrag.',
    technicalMessage: 'Invoice total amount with VAT (BT-112) = ...',
    area: 'totals',
  }],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** A fake backend: answers /view and /validate. A fresh Response per call, because a body can be read only once. */
function fakeBackend(view: () => Response, validate: () => Response) {
  const fetchMock = vi.fn(async (url: string) => (url.endsWith('/view') ? view() : validate()))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function chooseFile(file: File) {
  // The real <input type="file"> is hidden; tests can still fire a change on it.
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')!
  fireEvent.change(input, { target: { files: [file] } })
}

const xmlFile = () => new File(['<xml/>'], 'rechnung.xml', { type: 'text/xml' })

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows the invoice as a document and the check report next to it', async () => {
    const fetchMock = fakeBackend(() => jsonResponse(sampleInvoice), () => jsonResponse(validReport))
    render(<App />)

    chooseFile(xmlFile())

    expect(await screen.findByText('123456XX')).toBeTruthy()
    expect(screen.getByText('[Buyer name]')).toBeTruthy()
    expect(screen.getByText('04.04.2016')).toBeTruthy()
    expect(screen.getByText('DE79 0000 0000 1234 5678 90')).toBeTruthy()

    expect(await screen.findByText('Gültig')).toBeTruthy()
    expect(screen.getByRole('img', { name: /Geprüft nach XRechnung 3.0/ })).toBeTruthy()
    expect(screen.getByText('1 Hinweis')).toBeTruthy()

    const urls = fetchMock.mock.calls.map(([url]) => url)
    expect(urls).toEqual(['/api/invoices/validate', '/api/invoices/view'])
  })

  it('shows errors and highlights the matching part of the invoice', async () => {
    fakeBackend(() => jsonResponse(sampleInvoice), () => jsonResponse(invalidReport))
    render(<App />)

    chooseFile(xmlFile())

    expect(await screen.findByText('Nicht gültig')).toBeTruthy()
    expect(screen.getByRole('img', { name: /Nicht gültig, 1 Fehler/ })).toBeTruthy()

    const totals = document.querySelector('[data-area="totals"]')!
    expect(totals.className).not.toContain('doc__highlight')

    fireEvent.click(screen.getByRole('button', { name: /Der Gesamtbetrag stimmt nicht/ }))
    expect(totals.className).toContain('doc__highlight--error')

    // Clicking again removes the highlight.
    fireEvent.click(screen.getByRole('button', { name: /Der Gesamtbetrag stimmt nicht/ }))
    expect(totals.className).not.toContain('doc__highlight')
  })

  it('still shows the invoice when only the check fails', async () => {
    fakeBackend(() => jsonResponse(sampleInvoice), () => jsonResponse({ title: 'Fehler' }, 500))
    render(<App />)

    chooseFile(xmlFile())

    expect(await screen.findByText('Prüfung nicht möglich')).toBeTruthy()
    expect(screen.getByText('123456XX')).toBeTruthy()
    expect(screen.queryByRole('img', { name: /Geprüft|Nicht gültig/ })).toBeNull()
  })

  it('shows the German message from the backend when a file is unreadable', async () => {
    const problem = { title: 'Datei nicht lesbar', detail: 'Die Datei ist keine lesbare E-Rechnung.', status: 422 }
    fakeBackend(() => jsonResponse(problem, 422), () => jsonResponse(validReport))
    render(<App />)

    chooseFile(new File(['hallo'], 'notiz.txt', { type: 'text/plain' }))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByRole('heading', { name: 'Datei nicht lesbar' })).toBeTruthy()
    expect(within(alert).getByText('Die Datei ist keine lesbare E-Rechnung.')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Andere Datei wählen' }))
    expect(screen.getByRole('button', { name: 'Datei auswählen' })).toBeTruthy()
  })

  it('rejects files over 10 MB without uploading them', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    const big = new File(['x'], 'riesig.pdf', { type: 'application/pdf' })
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 })
    chooseFile(big)

    expect(await screen.findByRole('heading', { name: 'Datei zu groß' })).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('explains when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    render(<App />)

    chooseFile(xmlFile())

    expect(await screen.findByRole('heading', { name: 'Keine Verbindung' })).toBeTruthy()
  })
})
