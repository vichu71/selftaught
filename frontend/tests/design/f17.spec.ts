import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

// Vitest sustituye los .css por una cadena vacía (también con ?raw): se leen como ficheros
const read = (file: string) => readFileSync(join(process.cwd(), 'src/styles', file), 'utf8')
const tokens = read('tokens.css')
const components = read('components.css')
const motion = read('motion.css')
const ZONES = ['map', 'material', 'results', 'owner']

/** Quita los bloques `@media (...) { ... }` cuya condición cumple `pick`, con llaves anidadas. */
function stripMedia(css: string, pick: (condition: string) => boolean): string {
  let out = ''
  let i = 0
  while (i < css.length) {
    const at = css.indexOf('@media', i)
    if (at < 0) return out + css.slice(i)
    const open = css.indexOf('{', at)
    const condition = css.slice(at, open)
    out += css.slice(i, at)
    let depth = 0
    let j = open
    for (; j < css.length; j++) {
      if (css[j] === '{') depth++
      if (css[j] === '}' && --depth === 0) break
    }
    if (!pick(condition)) out += css.slice(at, j + 1)
    i = j + 1
  }
  return out
}

// F17 AC-1, ADR-96: tres tonos por zona, en claro y en noche
describe('F17 · tokens de zona', () => {
  const darkAt = tokens.search(/@media\s*\(prefers-color-scheme:\s*dark\)/)
  const lightPart = tokens.slice(0, darkAt)
  const darkPart = tokens.slice(darkAt)

  it.each(ZONES)('defines the three tones of %s as #rrggbb in light and in dark', (zone) => {
    for (const name of [`--color-zone-${zone}`, `--color-on-zone-${zone}`, `--color-zone-${zone}-ink`]) {
      expect(lightPart, `${name} (claro)`).toMatch(new RegExp(`${name}\\s*:\\s*#[0-9a-fA-F]{6}`))
      expect(darkPart, `${name} (noche)`).toMatch(new RegExp(`${name}\\s*:\\s*#[0-9a-fA-F]{6}`))
    }
  })

  it('gives every zone a different fill', () => {
    const fills = ZONES.map((z) => lightPart.match(new RegExp(`--color-zone-${z}\\s*:\\s*(#[0-9a-fA-F]{6})`))?.[1])
    expect(new Set(fills).size).toBe(4)
  })
})

// F17 AC-5, ADR-97: todo movimiento existe solo si el sistema no pide menos
describe('F17 · movimiento', () => {
  it('declares no animation, transition or keyframes outside the no-preference / reduce blocks', () => {
    for (const [file, css] of [['components.css', components], ['motion.css', motion]]) {
      const outside = stripMedia(css, (c) => /prefers-reduced-motion/.test(c))
      expect(outside, file).not.toMatch(/\banimation\s*:/)
      expect(outside, file).not.toMatch(/\btransition\s*:/)
      expect(outside, file).not.toMatch(/@keyframes/)
    }
  })

  it('animates the mascot wave, the plant bloom and the confetti, inside no-preference', () => {
    const inside = [components, motion].map((css) => stripMedia(css, (c) => !/no-preference/.test(c))).join('\n')
    expect(inside).toMatch(/mascot--wave|\.brand:hover/)
    expect(inside).toMatch(/\.plant--bloom/)
    expect(inside).toMatch(/\.confetti-piece/)
  })
})

// F17 ART-004: la barra, las zonas y la forma que acompaña al color
describe('F17 · estilos de la barra y de las zonas', () => {
  const css = components + motion

  it.each(ZONES)('sets the zone variables for %s from the shell and from the nav link', (zone) => {
    expect(css).toMatch(new RegExp(`\\.shell\\[data-zone=['"]${zone}['"]\\]`))
    expect(css).toMatch(new RegExp(`\\.nav-link\\[data-zone=['"]${zone}['"]\\]`))
  })

  it('reads the zone from variables, never from colours written by hand', () => {
    expect(css).toMatch(/--zone-fill\s*:\s*var\(--color-zone-map\)/)
    expect(css).toMatch(/\.nav-bubble[^}]*var\(--zone-fill\)/s)
  })

  it('marks the active link and the cards with a shape, not only with colour', () => {
    expect(css).toMatch(/\.nav-link\.is-active[^}]*(box-shadow|border-bottom)/s)
    expect(css).toMatch(/\.ui-card[^}]*border-top\s*:\s*[^;]*var\(--zone-fill\)/s)
  })

  it('keeps the tap target of the links, the bubbles and the bottom bar', () => {
    expect(css).toMatch(/\.nav-link\s*\{[^}]*min-height\s*:\s*var\(--size-tap\)/s)
    expect(css).toMatch(/\.nav-bubble/)
  })

  it('has the bubble and the header of the pages', () => {
    expect(css).toMatch(/\.page-bubble/)
    expect(css).toMatch(/\.page-header/)
  })
})
