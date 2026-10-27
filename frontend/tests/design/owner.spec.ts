import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

// Vitest sustituye los .css por una cadena vacía: se leen como ficheros
const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8')
const tokensCss = read('src/styles/tokens.css')

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
const light = vars(block(tokensCss, tokensCss.indexOf(':root')))
const darkMedia = mediaAt >= 0 ? block(tokensCss, mediaAt) : ''
const dark = { ...light, ...vars(block(darkMedia, darkMedia.indexOf(':root'))) }

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (a: string, b: string) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

const SERIES = ['--color-series-1', '--color-series-2', '--color-series-3', '--color-series-4']
const STATES = ['--color-stat-ok', '--color-stat-warn', '--color-stat-bad']

// F14 AC-13: los colores del panel viven solo en tokens.css y cumplen AA en claro y en noche
describe('panel del propietario: colores', () => {
  for (const [name, theme] of [['claro', light], ['noche', dark]] as const) {
    it(`every series colour reaches 3:1 against the surface in the ${name} theme`, () => {
      for (const token of SERIES) {
        expect(theme[token], `${token} existe en ${name}`).toMatch(/^#[0-9a-fA-F]{6}$/)
        expect(ratio(theme[token], theme['--color-surface']), `${token} sobre la superficie`).toBeGreaterThanOrEqual(3)
      }
    })

    it(`every state colour reaches 4.5:1 as text on the surface in the ${name} theme`, () => {
      for (const token of STATES) {
        expect(theme[token], `${token} existe en ${name}`).toMatch(/^#[0-9a-fA-F]{6}$/)
        expect(ratio(theme[token], theme['--color-surface']), `${token} sobre la superficie`).toBeGreaterThanOrEqual(4.5)
      }
    })
  }

  it('keeps the series colours distinguishable from each other', () => {
    const colors = SERIES.map((t) => (light[t] ?? '').toLowerCase())

    expect(new Set(colors).size).toBe(SERIES.length)
  })
})

describe('panel del propietario: estilos y plantillas', () => {
  const files = () => [
    'src/views/OwnerView.vue',
    ...readdirSync(join(process.cwd(), 'src/owner')).filter((f) => f.endsWith('.vue')).map((f) => `src/owner/${f}`),
  ]

  it('has no hex, rgb or hsl colour in any view or component of the panel', () => {
    for (const file of files()) {
      expect(read(file), file).not.toMatch(/#[0-9a-fA-F]{3,8}\b(?!-)|rgb\(|rgba\(|hsl\(/)
    }
  })

  it('never uses v-html, innerHTML, confirm() or alert() in the panel', () => {
    for (const file of files()) {
      const source = read(file)
      expect(source, file).not.toMatch(/v-html|innerHTML|outerHTML|insertAdjacentHTML/)
      expect(source, file).not.toMatch(/\b(window\.)?(confirm|alert|prompt)\(/)
    }
  })

  it('uses no third-party chart library: only the dependencies already in the package', () => {
    const pkg = JSON.parse(read('package.json'))

    expect(Object.keys(pkg.dependencies).sort()).toEqual(['@fontsource-variable/fredoka', '@fontsource-variable/newsreader', 'lucide-vue-next', 'marked', 'pinia', 'reka-ui', 'vue', 'vue-router'])
    for (const file of files()) expect(read(file), file).not.toMatch(/from '(chart\.js|echarts|d3|apexcharts|highcharts|plotly)/)
  })

  it('has panel styles that keep touch targets of 44 px and animate only inside prefers-reduced-motion: no-preference', () => {
    const css = read('src/styles/components.css')
    const at = css.indexOf('.owner-')

    expect(at, 'hay estilos .owner-* en components.css').toBeGreaterThanOrEqual(0)
    const owner = css.slice(at)
    expect(owner).toMatch(/var\(--size-tap\)/)
    const motion = [...owner.matchAll(/(transition|animation)\s*:/g)]
    for (const m of motion) {
      const before = owner.slice(0, m.index)
      expect(before.lastIndexOf('prefers-reduced-motion: no-preference'), 'una animación fuera de no-preference').toBeGreaterThan(before.lastIndexOf('\n}\n'))
    }
  })
})
