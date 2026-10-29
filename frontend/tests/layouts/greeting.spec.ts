import { describe, expect, it } from 'vitest'
import { daypart, firstName } from '@/layouts/greeting'

// F17 AC-4, AC-10: el saludo es texto plano, recortado, y la franja del día sale del reloj del navegador
describe('firstName', () => {
  it('takes the first word of the display name', () => {
    expect(firstName('victor huecas')).toBe('victor')
    expect(firstName('  Ana   María ')).toBe('Ana')
  })

  it('keeps apostrophes and accents', () => {
    expect(firstName("D'Arcy Núñez")).toBe("D'Arcy")
  })

  it('returns an empty string for a missing or blank name', () => {
    expect(firstName(undefined)).toBe('')
    expect(firstName(null)).toBe('')
    expect(firstName('   ')).toBe('')
  })

  it('cuts a very long name with an ellipsis, never beyond the maximum', () => {
    const out = firstName('a'.repeat(60))
    expect(out.length).toBeLessThanOrEqual(20)
    expect(out.endsWith('…')).toBe(true)
  })

  it('returns hostile text unchanged as plain text (the template escapes it)', () => {
    expect(firstName('<img src=x onerror=alert(1)>')).toBe('<img')
  })
})

describe('daypart', () => {
  const at = (h: number) => new Date(2026, 9, 29, h, 0, 0)
  it.each([[0, 'Buenas noches'], [5, 'Buenas noches'], [6, 'Buenos días'], [12, 'Buenos días'], [13, 'Buenas tardes'], [20, 'Buenas tardes'], [21, 'Buenas noches'], [23, 'Buenas noches']])('at %s h says %s', (h, text) => {
    expect(daypart(at(h))).toBe(text)
  })
})
