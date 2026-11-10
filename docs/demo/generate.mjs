// Creates the demo invoices in docs/demo from the official KoSIT sample 01.01a.
// Run from the project root:  node docs/demo/generate.mjs
// Names, addresses and e-mail domains are made up (.example is reserved for examples).
import { copyFile, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const samples = path.join(here, '../../backend/src/test/resources/samples')

/** Replaces text and stops if it isn't there, so a changed source can't silently produce a wrong file. */
function replace(text, from, to, { all = false } = {}) {
  if (!text.includes(from)) throw new Error(`Not found in source: ${from}`)
  return all ? text.replaceAll(from, to) : text.replace(from, to)
}

/** Turns the KoSIT placeholders into a realistic-looking invoice (works for UBL and CII). */
function realistic(xml, { ubl }) {
  let x = xml
  x = replace(x, '[Seller trading name]', 'Sonnenschein Verlag')
  x = replace(x, '[Seller name]', 'Fachverlag Sonnenschein GmbH')
  x = replace(x, '[Seller address line 1]', 'Musterstraße 12')
  x = replace(x, '[Seller city]', 'München')
  x = replace(x, '[Buyer name]', 'Café Morgenrot GmbH')
  x = replace(x, '[Buyer address line 1]', 'Hauptstraße 5')
  x = replace(x, '[Buyer city]', 'Nürnberg')
  x = replace(x, '[Buyer identifier]', 'K-4711')
  x = replace(x, '[HRA-Eintrag]', 'HRB 123456')
  x = replace(x, 'HRA-Eintrag in […]', 'HRB 123456, Amtsgericht München')
  x = replace(x, '[…]', 'fachverlag-sonnenschein.example/agb')
  // The seller's postcode comes first in the file, then the buyer's.
  x = replace(x, '>12345<', '>80331<')
  x = replace(x, '>12345<', '>90402<')
  x = replace(x, 'nicht vorhanden', 'Anna Berger')
  x = replace(x, '+49 1234-5678', '+49 89 1234567')
  x = replace(x, 'seller@email.de', 'rechnung@fachverlag-sonnenschein.example', { all: true })
  x = replace(x, 'buyer@info.de', 'buchhaltung@cafe-morgenrot.example', { all: true })
  x = replace(x, '123456XX', 'RE-2026-0147')
  x = replace(x, 'Zeitschrift [...]', 'Fachzeitschrift Gastronomie heute, Jahresabo', { all: true })
  x = replace(x, '12/2016', '12/2026')
  if (ubl) {
    x = replace(x, '2016-04-04', '2026-11-03')
    x = replace(x, '2016-01-01', '2026-01-01')
    x = replace(x, '2016-12-31', '2026-12-31')
  } else {
    x = replace(x, '20160404', '20261103')
    x = replace(x, '20160101', '20260101')
    x = replace(x, '20161231', '20261231')
  }
  return x
}

const write = (name, content) => writeFile(path.join(here, name), content, 'utf8')

const ubl = realistic(await readFile(path.join(samples, '01.01a-INVOICE_ubl.xml'), 'utf8'), { ubl: true })
const cii = realistic(await readFile(path.join(samples, '01.01a-INVOICE_uncefact.xml'), 'utf8'), { ubl: false })

// Valid invoices
await write('01-gueltig-xrechnung-ubl.xml', ubl)
await write('02-gueltig-xrechnung-cii.xml', cii)
await copyFile(path.join(samples, 'EN16931_Einfach.pdf'), path.join(here, '03-gueltig-zugferd.pdf'))

// 04 (wrong total) is made from the ZUGFeRD example; see README.md.

const noBuyerReference = (x) => replace(x, '<ram:BuyerReference>04011000-12345-03</ram:BuyerReference>', '')
const wrongTotal = (x) => replace(x, '<ram:GrandTotalAmount>336.9</ram:GrandTotalAmount>', '<ram:GrandTotalAmount>363.9</ram:GrandTotalAmount>')

await write('05-fehler-kaeuferreferenz.xml', noBuyerReference(cii))

let several = wrongTotal(noBuyerReference(cii))
several = several.replace(/\s*<ram:DefinedTradeContact>[\s\S]*?<\/ram:DefinedTradeContact>/, '') // no seller contact
several = replace(several, '<ram:CityName>Nürnberg</ram:CityName>', '') // no buyer city
await write('06-fehler-mehrere.xml', several)

// A down payment of 400 € that is larger than the total: the buyer gets 63.10 € back.
let prepaid = replace(cii, '<ram:DuePayableAmount>336.9</ram:DuePayableAmount>',
  '<ram:TotalPrepaidAmount>400.00</ram:TotalPrepaidAmount>\n                <ram:DuePayableAmount>-63.10</ram:DuePayableAmount>')
prepaid = replace(prepaid, 'RE-2026-0147', 'RE-2026-0148')
await write('07-anzahlung-guthaben.xml', prepaid)

await copyFile(path.join(samples, '04.05a-INVOICE_uncefact.xml'), path.join(here, '08-xrechnung-extension.xml'))

await writeFile(path.join(here, '09-keine-e-rechnung.pdf'), plainPdf([
  'Rechnung RE-2026-0150',
  'Eine normale PDF-Rechnung ohne eingebettete E-Rechnungsdaten.',
]))

console.log('Demo files written to', here)

/** A minimal one-page PDF with some text, written by hand (no library needed). */
function plainPdf(lines) {
  const text = lines.map((line, i) => `BT /F1 ${i === 0 ? 18 : 11} Tf 72 ${760 - i * 30} Td (${line}) Tj ET`).join('\n')
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = objects.map((body, i) => {
    const offset = pdf.length
    pdf += `${i + 1} 0 obj\n${body}\nendobj\n`
    return offset
  })
  const xref = pdf.length
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  pdf += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(pdf, 'latin1')
}
