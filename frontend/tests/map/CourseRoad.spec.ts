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

// F11 AC-5 a AC-7: el curso es un camino por un paisaje (los contratos de F10 siguen en CourseMap.spec)
describe('CourseMap · camino por un paisaje', () => {
  it('draws one road path through the stops (a smooth curve, not a straight line per stop)', () => {
    const w = render()

    const roads = w.findAll('path[data-testid="map-road"]')
    expect(roads.length).toBeGreaterThan(0)
    expect(roads[0].attributes('d')).toMatch(/^M /)
    expect(roads[0].attributes('d')).toMatch(/Q/)
    expect(w.find('line').exists()).toBe(false)
  })

  it('draws the part already travelled over the road', () => {
    const w = render()

    expect(w.find('path[data-testid="map-road-done"]').exists()).toBe(true)
    expect(render({ study: null }).find('path[data-testid="map-road-done"]').exists()).toBe(false)
  })

  it('draws every topic as a hill with its title, and keeps folded topics as hills too', async () => {
    const w = render()

    for (const id of ['t1', 't2', 't3']) expect(w.find(`[data-testid="map-hill-${id}"]`).exists()).toBe(true)
    await w.find('[data-testid="map-fold-t1"]').trigger('click')
    expect(w.find('[data-testid="map-hill-t1"]').exists()).toBe(true)
    expect(w.findAll('svg circle[data-kind="lesson"]').length).toBeLessThan(5)
  })

  it('shows the wanderer on the "now" stop only, and never when there is none', () => {
    expect(render().findAll('[data-testid="map-wanderer"]')).toHaveLength(1)

    const queued = outline().map((t) => ({
      ...t,
      chapters: t.chapters.map((c) => ({ ...c, lesson: piece(c.lesson!.id, 'PENDING') })),
      exam: piece(t.exam!.id, 'PENDING'),
    }))
    expect(render({ outline: queued, study: null, status: 'GENERATING' }).find('[data-testid="map-wanderer"]').exists()).toBe(false)
  })

  it('keeps the stops as stones (circles) and doors (squares) with their state in a class and in words', () => {
    const w = render()

    expect(w.find('circle[data-kind="lesson"].map-station--done').exists()).toBe(true)
    expect(w.find('rect[data-kind="exam"]').exists()).toBe(true)
    expect(w.find('[data-testid="map-item-l3"]').text()).toContain('Ahora')
  })

  it('names the "you are here" label in text, not only with the character', () => {
    expect(render().text()).toContain('TÚ ESTÁS AQUÍ')
  })

  it('draws a course of 80 stops without a road that leaves the picture', () => {
    const big = Array.from({ length: 10 }, (_, ti) => ({
      id: `T${ti}`, title: `Tema ${ti}`, summary: '',
      chapters: Array.from({ length: 7 }, (_, ci) => ({ id: `C${ti}-${ci}`, title: `Cap ${ci}`, objective: '', lesson: piece(`L${ti}-${ci}`), exercises: piece(`E${ti}-${ci}`) })),
      exam: piece(`X${ti}`),
    }))
    const w = render({ outline: big, study: null })
    const [, , width, height] = w.find('svg.course-map').attributes('viewBox')!.split(' ').map(Number)

    const numbers = (w.find('path[data-testid="map-road"]').attributes('d') ?? '').match(/-?\d+(\.\d+)?/g)!.map(Number)
    expect(numbers.filter((_, i) => i % 2 === 0).every((x) => x >= 0 && x <= width)).toBe(true)
    expect(numbers.filter((_, i) => i % 2 === 1).every((y) => y >= 0 && y <= height)).toBe(true)
  })
})
