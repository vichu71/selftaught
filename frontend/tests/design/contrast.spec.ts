import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

// Vitest sustituye los .css por una cadena vacía (también con ?raw): se leen como ficheros
const tokensCss = readFileSync(join(process.cwd(), 'src/styles/tokens.css'), 'utf8')

/** Devuelve el cuerpo `{ ... }` que empieza en la primera llave tras `from`, con llaves anidadas. */
function block(css: string, from: number): string {
  const open = css.indexOf('{', from)
  let depth = 0
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++
    if (css[i] === '}' && --depth === 0) return css.slice(open + 1, i)
  }
  return ''
}

const vars = (body: string) => Object.fromEntries([...body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]))

const mediaAt = tokensCss.search(/@media\s*\(prefers-color-scheme:\s*dark\)/)
const lightBody = block(tokensCss, tokensCss.indexOf(':root'))
const darkMedia = mediaAt >= 0 ? block(tokensCss, mediaAt) : ''
const light = vars(lightBody)
const dark = { ...light, ...vars(block(darkMedia, darkMedia.indexOf(':root'))) }

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
function ratio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

// Pares texto/fondo que deben llegar a 4,5:1 (AA) y elementos de interfaz que deben llegar a 3:1
const TEXT: [string, string][] = [
  ['--color-ink', '--color-paper'], ['--color-ink', '--color-surface'],
  ['--color-muted', '--color-paper'], ['--color-muted', '--color-surface'],
  ['--color-link', '--color-paper'], ['--color-link', '--color-surface'],
  ['--color-danger-text', '--color-paper'], ['--color-danger-text', '--color-surface'],
  ['--color-on-primary', '--color-primary'], ['--color-on-accent', '--color-accent'],
  ['--color-on-ok', '--color-ok'], ['--color-on-now', '--color-now'], ['--color-on-danger', '--color-danger'],
  // F11: el texto que se apoya en el paisaje (cielo y hierba)
  ['--color-ink', '--color-sky'], ['--color-muted', '--color-sky'], ['--color-link', '--color-sky'], ['--color-ink', '--color-grass'],
]
const UI: [string, string][] = [
  ['--color-line', '--color-paper'], ['--color-line', '--color-surface'],
  ['--color-focus', '--color-paper'], ['--color-focus', '--color-surface'], ['--color-focus', '--color-primary'],
  // F11: el camino y los contornos de las paradas se ven sobre la hierba y sobre el cielo
  ['--color-road', '--color-grass'], ['--color-line', '--color-grass'], ['--color-line', '--color-sky'], ['--color-focus', '--color-grass'],
]

// F10 AC-13, ADR-71: el contraste se comprueba sobre los tokens, en claro y en oscuro
describe('tokens.css · contraste', () => {
  it('declares every colour token of the contract as a #rrggbb value, in light and in dark', () => {
    const names = new Set([...TEXT, ...UI].flat())
    for (const theme of [light, dark]) {
      for (const name of names) expect(theme[name], name).toMatch(/^#[0-9a-fA-F]{6}$/)
    }
  })

  it('defines a dark palette of its own', () => {
    expect(darkMedia).not.toBe('')
    expect(Object.keys(vars(block(darkMedia, darkMedia.indexOf(':root')))).length).toBeGreaterThan(8)
  })

  for (const [theme, palette] of [['claro', light], ['oscuro', dark]] as const) {
    it.each(TEXT)(`${theme}: texto %s sobre %s llega a 4,5:1`, (fg, bg) => {
      expect(ratio(palette[fg], palette[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(4.5)
    })

    it.each(UI)(`${theme}: elemento %s sobre %s llega a 3:1`, (fg, bg) => {
      expect(ratio(palette[fg], palette[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(3)
    })
  }

  it('the state colours of the map differ from each other (done, now, to come)', () => {
    for (const palette of [light, dark]) {
      expect(new Set([palette['--color-ok'], palette['--color-now'], palette['--color-surface']]).size).toBe(3)
    }
  })

  // F11 AC-8: las ilustraciones solo usan colores de tokens, definidos como #rrggbb en claro y en oscuro
  it('defines every illustration colour in light and in dark, and the sky differs from the grass', () => {
    for (const palette of [light, dark]) {
      for (const name of ['--color-sky', '--color-grass', '--color-leaf', '--color-trunk', '--color-soil', '--color-sun', '--color-fruit', '--color-cloud', '--color-road']) {
        expect(palette[name], name).toMatch(/^#[0-9a-fA-F]{6}$/)
      }
      expect(palette['--color-sky']).not.toBe(palette['--color-grass'])
    }
  })

  it('the night landscape is darker than the day one', () => {
    expect(luminance(dark['--color-sky'])).toBeLessThan(luminance(light['--color-sky']))
    expect(luminance(dark['--color-grass'])).toBeLessThan(luminance(light['--color-grass']))
  })
})
