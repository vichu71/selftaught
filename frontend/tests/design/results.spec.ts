import { describe, expect, it } from 'vitest'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

// Vitest sustituye los .css por una cadena vacía: se leen como ficheros
const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8')
const exists = (path: string) => existsSync(join(process.cwd(), path))
const files = (dir: string, ext: RegExp) => (exists(dir) ? readdirSync(join(process.cwd(), dir)).filter((f) => ext.test(f)).map((f) => `${dir}/${f}`) : [])

// F15 (ADR-93): los componentes de cifras suben a ui/ y Resultados usa los suyos
describe('componentes compartidos en ui/ (ADR-93)', () => {
  it('moved StatCard, BarList and format to src/ui and left nothing behind in src/owner', () => {
    for (const file of ['StatCard.vue', 'BarList.vue', 'format.ts']) {
      expect(exists(`src/ui/${file}`), `src/ui/${file}`).toBe(true)
      expect(exists(`src/owner/${file}`), `src/owner/${file} ya no existe`).toBe(false)
    }
    expect(exists('src/ui/BackLink.vue')).toBe(true)
  })

  it('has no import of the moved files from their old place', () => {
    const owner = ['src/views/OwnerView.vue', ...files('src/owner', /\.(vue|ts)$/)].map((f) => read(f)).join('\n')
    expect(owner).not.toMatch(/@\/owner\/(StatCard|BarList|format)/)
    expect(owner).not.toMatch(/from '\.\/format'/)
  })

  it('renamed the classes of the moved components from owner-* to ui-*', () => {
    for (const file of ['src/ui/StatCard.vue', 'src/ui/BarList.vue']) {
      expect(read(file), file).not.toMatch(/owner-/)
      expect(read(file), file).toMatch(/ui-(stat|bars?)/)
    }
    const css = read('src/styles/components.css')
    expect(css).toMatch(/\.ui-stat\b/)
    expect(css).toMatch(/\.ui-bars\b/)
    expect(css).not.toMatch(/\.owner-stat\b|\.owner-bars\b|\.owner-bar-/)
  })
})

describe('Resultados: estilos y plantillas (F15)', () => {
  const sources = () => ['src/views/ResultsView.vue', ...files('src/results', /\.vue$/), 'src/ui/BackLink.vue', 'src/ui/StatCard.vue', 'src/ui/BarList.vue']

  it('has the new components', () => {
    for (const file of ['TopeMeter.vue', 'CourseCard.vue', 'taskLabels.ts']) expect(exists(`src/results/${file}`), file).toBe(true)
  })

  it('has no colour by hand, v-html, innerHTML, confirm() or alert() in any of them', () => {
    for (const file of sources()) {
      const code = read(file)
      expect(code, file).not.toMatch(/#[0-9a-fA-F]{3,8}\b(?!-)|rgb\(|rgba\(|hsl\(/)
      expect(code, file).not.toMatch(/v-html|innerHTML|outerHTML|insertAdjacentHTML/)
      expect(code, file).not.toMatch(/\b(window\.)?(confirm|alert|prompt)\(/)
    }
  })

  it('translates every task of the backend enumeration', () => {
    const labels = read('src/results/taskLabels.ts')
    for (const task of ['OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING', 'INTAKE', 'IMAGE']) expect(labels, task).toContain(task)
    for (const word of ['Temario', 'Lecciones', 'Ejercicios y exámenes', 'Tutor', 'Material', 'Asistente', 'Ilustraciones']) expect(labels, word).toContain(word)
  })

  it('has styles for the cards, the meter and the grid, and animates only inside prefers-reduced-motion: no-preference', () => {
    const css = read('src/styles/components.css')
    for (const rule of ['.result-grid', '.result-card', '.tope-meter', '.back-link']) expect(css, rule).toContain(rule)
    const from = css.indexOf('.result-grid')
    const block = css.slice(from)
    for (const m of block.matchAll(/(transition|animation)\s*:/g)) {
      const before = block.slice(0, m.index)
      expect(before.lastIndexOf('prefers-reduced-motion: no-preference'), 'una animación fuera de no-preference').toBeGreaterThan(before.lastIndexOf('\n}\n'))
    }
  })

  it('stacks the course cards on a phone', () => {
    const css = read('src/styles/components.css')

    expect(css).toMatch(/\.result-grid\s*\{[^}]*grid-template-columns:\s*repeat\(auto-fill,\s*minmax\(/)
  })

  it('uses only dependencies already in the package', () => {
    const pkg = JSON.parse(read('package.json'))

    expect(Object.keys(pkg.dependencies).sort()).toEqual(['@fontsource-variable/fredoka', '@fontsource-variable/newsreader', 'lucide-vue-next', 'marked', 'pinia', 'reka-ui', 'vue', 'vue-router'])
  })
})
