import { describe, expect, it } from 'vitest'
import indexHtml from '../index.html?raw'

const sources = import.meta.glob('/src/**/*.{vue,ts}', { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>

// Reglas de PROJECT.md §8 aplicadas al código del frontend
describe('frontend source policy', () => {
  it('has source files to inspect', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(5)
  })

  it('never uses v-html or innerHTML', () => {
    const offenders = Object.entries(sources).filter(([, code]) => /v-html|innerHTML|outerHTML|insertAdjacentHTML/.test(code))
    expect(offenders.map(([file]) => file)).toEqual([])
  })

  it('never keeps tokens or credentials in web storage (the session is an HttpOnly cookie)', () => {
    const offenders = Object.entries(sources).filter(([, code]) => /localStorage|sessionStorage|indexedDB/.test(code))
    expect(offenders.map(([file]) => file)).toEqual([])
  })

  it('does not embed secrets or absolute backend URLs', () => {
    const offenders = Object.entries(sources).filter(([, code]) =>
      /(sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}|https?:\/\/(localhost|127\.0\.0\.1|backend))/.test(code),
    )
    expect(offenders.map(([file]) => file)).toEqual([])
  })

  // Diseño responsive desde el principio (AC-9)
  it('declares the responsive viewport meta tag', () => {
    expect(indexHtml).toMatch(/<meta[^>]+name=["']viewport["'][^>]+width=device-width/)
  })
})
