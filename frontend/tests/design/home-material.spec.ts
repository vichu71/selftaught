import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

// Vitest sustituye los .css por una cadena vacía: se leen como ficheros
const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8')
const exists = (path: string) => existsSync(join(process.cwd(), path))
const css = () => read('src/styles/components.css')

const NEW_FILES = [
  'src/ui/ActionCard.vue', 'src/ui/Disclosure.vue', 'src/ui/EmptyNote.vue', 'src/material/DropZone.vue', 'src/components/StudentCard.vue',
]

// F16 (ADR-94): componentes y estilos nuevos de Inicio y Mi material
describe('Inicio y Mi material: plantillas y estilos (F16)', () => {
  it('has the new components', () => {
    for (const file of NEW_FILES) expect(exists(file), file).toBe(true)
  })

  it('has no colour by hand, v-html, innerHTML, confirm() or alert() in any of them or in the views and lists they change', () => {
    const files = [...NEW_FILES, 'src/views/HomeView.vue', 'src/views/MaterialView.vue', 'src/components/StudentsPanel.vue', 'src/components/MaterialList.vue', 'src/components/MaterialStatus.vue']
    for (const file of files) {
      const code = read(file)
      expect(code, file).not.toMatch(/#[0-9a-fA-F]{3,8}\b(?!-)|rgb\(|rgba\(|hsl\(/)
      expect(code, file).not.toMatch(/v-html|innerHTML|outerHTML|insertAdjacentHTML/)
      expect(code, file).not.toMatch(/\b(window\.)?(confirm|alert|prompt)\(/)
    }
  })

  it('has styles for every new piece, with tokens', () => {
    const sheet = css()
    for (const rule of ['.action-card', '.shortcut-grid', '.disclosure', '.empty-note', '.student-card', '.drop-zone', '.doc-card']) expect(sheet, rule).toContain(rule)
    expect(sheet).toMatch(/\.drop-zone\.is-over|\.drop-zone\s*\.is-over|\.is-over/)
    expect(sheet).toMatch(/\.drop-zone\.is-disabled|\.is-disabled/)
  })

  it('keeps a tap target of 44 px in the cards and the zone, and the long names break instead of overflowing', () => {
    const sheet = css()

    for (const rule of ['.action-card', '.drop-zone']) {
      const block = sheet.match(new RegExp(`${rule.replace('.', '\\.')}\\s*\\{[^}]*\\}`))
      expect(block, rule).not.toBeNull()
      expect(block![0], rule).toMatch(/var\(--size-tap\)/)
    }
    expect(sheet).toMatch(/\.doc-card[^{]*\{[^}]*overflow-wrap:\s*anywhere/)
    expect(sheet).toMatch(/\.student-card[^{]*\{[^}]*overflow-wrap:\s*anywhere/)
  })

  it('animates only inside prefers-reduced-motion: no-preference', () => {
    const sheet = css()
    const from = sheet.indexOf('.action-card')
    const block = sheet.slice(from)

    for (const m of block.matchAll(/(transition|animation)\s*:/g)) {
      const before = block.slice(0, m.index)
      expect(before.lastIndexOf('prefers-reduced-motion: no-preference'), 'una animación fuera de no-preference').toBeGreaterThan(before.lastIndexOf('\n}\n'))
    }
  })

  it('makes the drop zone the label of the real file input, hidden only visually', () => {
    const code = read('src/material/DropZone.vue')

    expect(code).toMatch(/<label[^>]*for=/)
    expect(code).toMatch(/type="file"/)
    expect(code).toMatch(/visually-hidden/)
    expect(code).not.toMatch(/display:\s*none/)
    expect(code).toMatch(/dragover/)
    expect(code).toMatch(/drop/)
  })

  it('has no loose underlined link sentences left in Inicio', () => {
    const code = read('src/views/HomeView.vue')

    expect(code).not.toMatch(/<p><RouterLink to="\/(material|codigo|resultados)"/)
    expect(code).toMatch(/ActionCard/)
  })

  it('uses the shared components in the screens', () => {
    expect(read('src/views/MaterialView.vue')).toMatch(/DropZone/)
    expect(read('src/views/MaterialView.vue')).toMatch(/EmptyNote/)
    expect(read('src/components/StudentsPanel.vue')).toMatch(/Disclosure/)
    expect(read('src/components/StudentsPanel.vue')).toMatch(/StudentCard/)
    expect(read('src/components/CoursesPanel.vue')).toMatch(/EmptyNote/)
  })

  it('uses only dependencies already in the package', () => {
    const pkg = JSON.parse(read('package.json'))

    expect(Object.keys(pkg.dependencies).sort()).toEqual(['@fontsource-variable/fredoka', '@fontsource-variable/newsreader', 'lucide-vue-next', 'marked', 'pinia', 'reka-ui', 'vue', 'vue-router'])
  })
})
