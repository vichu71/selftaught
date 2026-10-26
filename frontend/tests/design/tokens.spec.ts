import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'

// Vitest sustituye los .css por una cadena vacía (también con ?raw): se leen como ficheros
const SRC = join(process.cwd(), 'src')
const styles: Record<string, string> = Object.fromEntries(
  (readdirSync(SRC, { recursive: true }) as string[])
    .filter((f) => f.endsWith('.css'))
    .map((f) => ['/src/' + f.split('\\').join('/'), readFileSync(join(SRC, f), 'utf8')]),
)
const sources = import.meta.glob('/src/**/*.{vue,ts}', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

const REQUIRED_TOKENS = [
  '--color-paper', '--color-surface', '--color-ink', '--color-muted', '--color-line', '--color-focus', '--color-link',
  '--color-primary', '--color-on-primary', '--color-accent', '--color-on-accent', '--color-ok', '--color-on-ok',
  '--color-now', '--color-on-now', '--color-danger', '--color-on-danger', '--color-danger-text',
  '--space-1', '--space-2', '--space-3', '--space-4', '--space-5', '--space-6',
  '--radius-1', '--radius-2', '--radius-pill', '--border-width', '--shadow-hard',
  '--font-display', '--font-reading', '--font-mono', '--size-tap',
]

// F10 AC-1: todo sale de los tokens; ninguna vista lleva colores a mano
describe('design tokens', () => {
  const tokens = styles['/src/styles/tokens.css'] ?? ''

  it('has the single tokens file with the whole contract', () => {
    expect(tokens).not.toBe('')
    for (const name of REQUIRED_TOKENS) expect(tokens, name).toMatch(new RegExp(`${name}\\s*:`))
  })

  it('keeps colours only in tokens.css (not in other stylesheets, views or components)', () => {
    const colour = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/
    const css = Object.entries(styles).filter(([file]) => !file.endsWith('/tokens.css'))
    const vue = Object.entries(sources).filter(([file]) => file.endsWith('.vue'))
    const offenders = [...css, ...vue].filter(([, code]) => colour.test(code.replace(/&#\d+;/g, ''))).map(([file]) => file)
    expect(offenders).toEqual([])
  })

  // AC-14: objetivos táctiles de al menos 44 px
  it('sets a tap target of at least 44 px', () => {
    const m = tokens.match(/--size-tap\s*:\s*([\d.]+)(rem|px)/)
    expect(m).not.toBeNull()
    const px = m![2] === 'rem' ? parseFloat(m![1]) * 16 : parseFloat(m![1])
    expect(px).toBeGreaterThanOrEqual(44)
  })

  it('shows the focus on every interactive element', () => {
    const base = styles['/src/styles/base.css'] ?? ''
    expect(base).toMatch(/:focus-visible/)
    expect(base).toMatch(/var\(--color-focus\)/)
  })
})

// AC-12, ADR-70: fuentes en el propio paquete
describe('fonts', () => {
  const fonts = styles['/src/styles/fonts.css'] ?? ''

  it('declares local @font-face rules with swap and no external URL', () => {
    expect(fonts).toMatch(/@font-face/)
    expect(fonts).toMatch(/font-display:\s*swap/)
    expect(fonts).not.toMatch(/https?:\/\//)
  })

  it('loads the fonts from the app itself, in index.html and in every stylesheet', () => {
    expect(indexHtml).not.toMatch(/fonts\.googleapis|fonts\.gstatic|cdn\./i)
    for (const [file, css] of Object.entries(styles)) expect(css, file).not.toMatch(/@import\s+url\(\s*['"]?https?:/)
  })

  it('names the three families of the design', () => {
    const tokens = styles['/src/styles/tokens.css'] ?? ''
    expect(tokens).toMatch(/--font-display:[^;]*Bricolage Grotesque/)
    expect(tokens).toMatch(/--font-reading:[^;]*Newsreader/)
    expect(tokens).toMatch(/--font-mono:[^;]*Space Mono/)
  })
})

// AC-15: movimiento
describe('motion', () => {
  it('turns off every transition and animation with prefers-reduced-motion', () => {
    const motion = styles['/src/styles/motion.css'] ?? ''
    expect(motion).toMatch(/prefers-reduced-motion:\s*reduce/)
    expect(motion).toMatch(/animation(-duration)?:\s*(none|0(\.0+)?1?ms)/)
    expect(motion).toMatch(/transition(-duration)?:\s*(none|0(\.0+)?1?ms)/)
  })

  it('has no infinite animations outside a reduced-motion guard', () => {
    for (const [file, css] of Object.entries(styles)) {
      const unguarded = css.split(/@media\s*\(prefers-reduced-motion:\s*no-preference\)/)[0]
      expect(unguarded, file).not.toMatch(/animation[^;]*infinite/)
    }
  })
})
