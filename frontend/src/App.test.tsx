import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { InvoiceView } from './api/types'

// Shaped like the backend's real answer for the KoSIT sample 01.01a.
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
  totals: { net: 314.86, vat: 22.04, gross: 336.9, duePayable: 336.9 },
  payment: { terms: 'Zahlbar sofort ohne Abzug.', iban: 'DE79000000001234567890' },
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function chooseFile(file: File) {
  // The real <input type="file"> is hidden; tests can still fire a change on it.
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')!
  fireEvent.change(input, { target: { files: [file] } })
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows the uploaded invoice as a document', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(sampleInvoice))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    chooseFile(new File(['<xml/>'], 'rechnung.xml', { type: 'text/xml' }))

    expect(await screen.findByText('123456XX')).toBeTruthy()
    expect(screen.getByText('[Buyer name]')).toBeTruthy()
    expect(screen.getByText('04.04.2016')).toBeTruthy()
    expect(screen.getByText('DE79 0000 0000 1234 5678 90')).toBeTruthy()
    expect(screen.getByText('rechnung.xml')).toBeTruthy()

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/invoices/view')
    expect((options.body as FormData).get('file')).toBeInstanceOf(File)
  })

  it('shows the German message from the backend when a file is unreadable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      title: 'Datei nicht lesbar',
      detail: 'Die Datei ist keine lesbare E-Rechnung.',
      status: 422,
    }, 422)))
    render(<App />)

    chooseFile(new File(['hallo'], 'notiz.txt', { type: 'text/plain' }))

    expect(await screen.findByRole('heading', { name: 'Datei nicht lesbar' })).toBeTruthy()
    expect(screen.getByText('Die Datei ist keine lesbare E-Rechnung.')).toBeTruthy()

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

    chooseFile(new File(['<xml/>'], 'rechnung.xml', { type: 'text/xml' }))

    expect(await screen.findByRole('heading', { name: 'Keine Verbindung' })).toBeTruthy()
  })
})
