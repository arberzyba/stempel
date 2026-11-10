// German display formats for invoice data.

const DASH = '–'

const moneyFormats = new Map<string, Intl.NumberFormat>()

/** 1234.5 → "1.234,50 €" (in the invoice's own currency). */
export function formatMoney(amount: number | null | undefined, currency: string | null = 'EUR'): string {
  if (amount == null) return DASH
  const code = currency ?? 'EUR'
  let format = moneyFormats.get(code)
  if (!format) {
    try {
      format = new Intl.NumberFormat('de-DE', { style: 'currency', currency: code })
    } catch {
      // Unknown currency code in the file: show the plain number and the code.
      return `${formatNumber(amount, 2)} ${code}`
    }
    moneyFormats.set(code, format)
  }
  return format.format(amount)
}

/** 20 → "20", 1.5 → "1,5" */
export function formatNumber(value: number | null | undefined, minimumFractionDigits = 0): string {
  if (value == null) return DASH
  return new Intl.NumberFormat('de-DE', { minimumFractionDigits, maximumFractionDigits: 4 }).format(value)
}

/** 19 → "19 %" */
export function formatPercent(value: number | null | undefined): string {
  if (value == null) return DASH
  return `${formatNumber(value)} %`
}

/**
 * "2024-11-15" → "15.11.2024". We split the text instead of using new Date(),
 * because new Date("2024-11-15") means midnight UTC and can show the previous
 * day in time zones west of UTC.
 */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return DASH
  const [year, month, day] = isoDate.split('-')
  return day && month && year ? `${day}.${month}.${year}` : isoDate
}

/** "DE79000000001234567890" → "DE79 0000 0000 1234 5678 90" */
export function formatIban(iban: string | null | undefined): string {
  if (!iban) return DASH
  return iban.replace(/\s+/g, '').replace(/(.{4})(?=.)/g, '$1 ')
}

// Unit codes from UN/ECE Recommendation 20, as used in EN 16931 (BT-130).
const UNITS: Record<string, string> = {
  C62: 'Stk.',
  H87: 'Stk.',
  XPP: 'Stk.',
  EA: 'Stk.',
  HUR: 'Std.',
  MIN: 'Min.',
  DAY: 'Tag',
  WEE: 'Woche',
  MON: 'Monat',
  ANN: 'Jahr',
  KGM: 'kg',
  GRM: 'g',
  TNE: 't',
  MTR: 'm',
  KMT: 'km',
  MTK: 'm²',
  MTQ: 'm³',
  LTR: 'l',
  KWH: 'kWh',
  LS: 'pauschal',
  SET: 'Satz',
  PR: 'Paar',
}

/** "H87" → "Stk."; unknown codes are shown as they are. */
export function unitLabel(code: string | null | undefined): string {
  if (!code) return ''
  return UNITS[code] ?? code
}

// VAT category codes (UNCL 5305, BT-118).
const VAT_CATEGORIES: Record<string, string> = {
  S: '',
  Z: 'Nullsatz',
  E: 'steuerbefreit',
  AE: 'Steuerschuldnerschaft des Leistungsempfängers',
  K: 'innergemeinschaftliche Lieferung',
  G: 'Ausfuhr',
  O: 'nicht steuerbar',
  L: 'IGIC',
  M: 'IPSI',
}

/** "S" (normal rate) → "", "AE" → "Steuerschuldnerschaft des Leistungsempfängers" */
export function vatCategoryLabel(code: string | null | undefined): string {
  if (!code) return ''
  return VAT_CATEGORIES[code] ?? code
}
