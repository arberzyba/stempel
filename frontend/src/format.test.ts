import { describe, expect, it } from 'vitest'
import { formatDate, formatIban, formatMoney, formatPercent, unitLabel } from './format'

// Intl puts a non-breaking space (U+00A0) between the number and the euro sign.
const NBSP = String.fromCharCode(0xa0)

describe('formatMoney', () => {
  it('uses German separators and the euro sign', () => {
    expect(formatMoney(1234.5)).toBe(`1.234,50${NBSP}€`)
  })

  it('uses the invoice currency', () => {
    expect(formatMoney(10, 'USD')).toBe(`10,00${NBSP}$`)
  })

  it('shows a dash for missing amounts', () => {
    expect(formatMoney(null)).toBe('–')
  })

  it('does not crash on an unknown currency code', () => {
    expect(formatMoney(5, 'XYZ1')).toBe('5,00 XYZ1')
  })
})

describe('formatDate', () => {
  it('turns an ISO date into the German format', () => {
    expect(formatDate('2024-11-15')).toBe('15.11.2024')
  })

  it('shows a dash for a missing date', () => {
    expect(formatDate(null)).toBe('–')
  })
})

describe('other formats', () => {
  it('groups an IBAN in blocks of four', () => {
    expect(formatIban('DE79000000001234567890')).toBe('DE79 0000 0000 1234 5678 90')
  })

  it('formats percentages', () => {
    expect(formatPercent(19)).toBe('19 %')
    expect(formatPercent(7.5)).toBe('7,5 %')
  })

  it('translates unit codes and keeps unknown ones', () => {
    expect(unitLabel('H87')).toBe('Stk.')
    expect(unitLabel('HUR')).toBe('Std.')
    expect(unitLabel('ABC')).toBe('ABC')
  })
})
