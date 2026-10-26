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

  // F10 AC-11: ni plantillas con HTML crudo ni SVG montado como cadena
  it('never renders raw HTML: no {@html}, no render functions that set innerHTML/domProps, no SVG built from strings', () => {
    const offenders = Object.entries(sources).filter(([, code]) => /\{@html|domProps|createContextualFragment|DOMParser|new XMLSerializer|document\.write/.test(code))
    expect(offenders.map(([file]) => file)).toEqual([])
  })

  // F10 AC-11, ADR-70: nada se pide a terceros desde la aplicación
  it('loads nothing from third parties: no external URLs in sources or in index.html', () => {
    const external = /https?:\/\/(?!www\.w3\.org\/(2000\/svg|1999\/xlink))/
    const offenders = Object.entries(sources).filter(([, code]) => external.test(code)).map(([file]) => file)
    expect(offenders).toEqual([])
    expect(indexHtml).not.toMatch(/(src|href)=["']https?:\/\//)
    expect(indexHtml).not.toMatch(/<script[^>]+src=["']https?:/)
  })

  // F10 AC-11: la política del servidor web no se relaja para dar cabida al diseño
  it('keeps the web server policy closed to its own origin', async () => {
    const nginx = (await import('../nginx.conf?raw')).default
    expect(nginx).toMatch(/Content-Security-Policy "default-src 'self'/)
    expect(nginx).not.toMatch(/script-src[^;"]*(unsafe-inline|unsafe-eval|https?:)/)
    expect(nginx).not.toMatch(/(font|style|img|connect)-src[^;"]*https?:/)
  })

  // Diseño responsive desde el principio (AC-9)
  it('declares the responsive viewport meta tag', () => {
    expect(indexHtml).toMatch(/<meta[^>]+name=["']viewport["'][^>]+width=device-width/)
  })
})
