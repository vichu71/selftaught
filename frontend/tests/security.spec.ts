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

  // ADR-45 (F05): `marked` solo se usa para LEER el Markdown en tokens; nunca para producir HTML
  it('uses the Markdown library only inside MarkdownContent and only to read tokens', () => {
    const importers = Object.entries(sources).filter(([, code]) => /from ['"]marked['"]|require\(['"]marked['"]\)/.test(code))
    expect(importers.map(([file]) => file)).toEqual(['/src/components/MarkdownContent.vue'])
    const code = Object.entries(sources).filter(([, c]) => /marked/.test(c)).map(([, c]) => c).join('\n')
    expect(code).toMatch(/lexer/)
    expect(code).not.toMatch(/marked\.parse\(|marked\.parseInline\(|\bmarked\(|new Marked|\.use\(/)
    expect(code).not.toMatch(/Renderer/)
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
