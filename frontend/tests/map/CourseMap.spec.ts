import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import CourseMap from '@/map/CourseMap.vue'
import { outline, piece, study } from './fixtures'

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div/>' } }] })

const render = (props: Record<string, unknown> = {}) =>
  mount(CourseMap, {
    props: { courseId: 'c1', outline: outline(), study: study(), status: 'READY', ...props },
    global: { plugins: [router] },
  })

const item = (w: ReturnType<typeof render>, id: string) => w.find(`[data-testid="map-item-${id}"]`)

// F10 AC-4 a AC-6: el mapa del curso y su lista equivalente
describe('CourseMap', () => {
  it('draws the line as an image with a summary and gives the same stops as an ordered list', () => {
    const w = render()

    const svg = w.find('[data-testid="course-map"]')
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toMatch(/2 de 5 lecciones/)
    expect(w.find('ol[data-testid="map-list"]').exists()).toBe(true)
    expect(w.findAll('[data-testid^="map-item-"]')).toHaveLength(8)
  })

  it('says each state in words, not only with colour or shape', () => {
    const w = render()

    expect(item(w, 'l1').text()).toContain('Hecha')
    expect(item(w, 'x1').text()).toContain('superado')
    expect(item(w, 'l3').text()).toContain('Ahora')
    expect(item(w, 'l4').text()).toContain('En cola')
    expect(item(w, 'l5').text()).toContain('Por venir')
  })

  it('marks the "now" stop as the current step, and only that one', () => {
    const w = render()

    expect(item(w, 'l3').attributes('aria-current')).toBe('step')
    expect(w.findAll('[aria-current="step"]')).toHaveLength(1)
  })

  it('links ready pieces to their page and leaves queued ones as plain text', () => {
    const w = render()

    expect(w.find('[data-testid="map-link-l3"]').attributes('href')).toBe('/cursos/c1/piezas/l3')
    expect(w.find('[data-testid="map-link-x1"]').attributes('href')).toBe('/cursos/c1/piezas/x1')
    expect(w.find('[data-testid="map-link-l4"]').exists()).toBe(false)
  })

  it('shows failed and outdated pieces with their own text', () => {
    const o = outline()
    o[1].chapters[1].lesson = piece('l4', 'FAILED')
    o[0].chapters[0].lesson = piece('l1', 'DONE', true)
    const w = render({ outline: o })

    expect(item(w, 'l4').text()).toContain('Hubo un error')
    expect(item(w, 'l1').text()).toContain('La lección cambió')
  })

  it('does not invent a "now" while the course is being generated, and says so', () => {
    const queued = outline().map((t) => ({
      ...t,
      chapters: t.chapters.map((c) => ({ ...c, lesson: piece(c.lesson!.id, 'PENDING') })),
      exam: piece(t.exam!.id, 'PENDING'),
    }))
    const w = render({ outline: queued, study: null, status: 'GENERATING' })

    expect(w.findAll('[aria-current="step"]')).toHaveLength(0)
    expect(w.find('[data-testid="map-no-now"]').text()).toMatch(/preparando/i)
  })

  it('says the course is finished when everything is done', () => {
    const s = study()
    s.topics.forEach((t) => {
      t.lessons.forEach((l) => (l.done = true))
      if (t.exam) t.exam.passed = true
    })
    const o = outline().map((t) => ({
      ...t,
      chapters: t.chapters.map((c) => ({ ...c, lesson: piece(c.lesson!.id) })),
      exam: piece(t.exam!.id),
    }))
    const w = render({ outline: o, study: s })

    expect(w.find('[data-testid="map-finished"]').exists()).toBe(true)
  })

  it('renders an empty outline without breaking', () => {
    const w = render({ outline: [] })

    expect(w.find('[data-testid="map-list"]').findAll('li')).toHaveLength(0)
  })

  it('folds a topic and unfolds it again, keeping its stops in the list', async () => {
    const w = render()

    await w.find('[data-testid="map-fold-t1"]').trigger('click')

    expect(w.find('[data-testid="map-fold-t1"]').attributes('aria-expanded')).toBe('false')
    expect(item(w, 'l1').exists()).toBe(true)
    await w.find('[data-testid="map-fold-t1"]').trigger('click')
    expect(w.find('[data-testid="map-fold-t1"]').attributes('aria-expanded')).toBe('true')
  })

  // AC-11: texto de la IA o del usuario siempre como texto
  it('shows hostile titles as text, in the list and in the drawing', () => {
    const o = outline()
    o[0].title = '<script>alert(1)</script>'
    o[0].chapters[0].title = '<img src=x onerror=alert(1)>'
    const w = render({ outline: o })

    expect(w.html()).not.toContain('<script')
    expect(w.find('img').exists()).toBe(false)
    expect(w.findAll('*').some((el) => el.element.getAttributeNames().some((n) => n.startsWith('on')))).toBe(false)
    expect(w.text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('does not rely on colour alone: every station kind has a shape', () => {
    const w = render()

    expect(w.findAll('svg circle[data-kind="lesson"]').length).toBe(5)
    expect(w.findAll('svg rect[data-kind="exam"]').length).toBe(3)
  })
})
