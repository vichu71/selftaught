import { describe, expect, it } from 'vitest'
import CourseCard from '@/results/CourseCard.vue'
import { adult, mountApp } from '../helpers'

const course = (over: object = {}) => ({
  id: 'c1', title: 'Java básico', status: 'READY', done: 42, failed: 0, pending: 0, total: 42, calls: 43, costUsd: 0.0612, createdAt: '2026-10-25T10:00:00Z', ...over,
})

// F15 AC-5 (D5): cada curso es una tarjeta con su planta, su barra de piezas y su estado dicho en palabras
describe('CourseCard', () => {
  const open = (over: object = {}, showMoney = true) => mountApp(CourseCard, { user: adult, route: '/resultados', props: { course: course(over), showMoney } })

  it.each([[42, 42, 'fruit'], [20, 40, 'tree'], [8, 20, 'tree'], [1, 100, 'sprout'], [0, 10, 'seed']])('draws the plant of the %s of %s pieces done as «%s»', async (done, total, stage) => {
    const { wrapper } = await open({ done, total })

    expect(wrapper.get('svg.plant').attributes('data-stage')).toBe(stage)
  })

  it('is a seed that is still getting ready, with no bar of pieces and no percent, when it has no pieces yet', async () => {
    const { wrapper } = await open({ status: 'PENDING_GENERATION', done: 0, total: 0, calls: 0 })

    expect(wrapper.get('svg.plant').attributes('data-stage')).toBe('seed')
    expect(wrapper.text()).toContain('Preparándose')
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('NaN')
  })

  it('shows the title as a link to the course page, the state in words and the pieces done as text', async () => {
    const { wrapper } = await open()

    const link = wrapper.get('[data-testid="result-link-c1"]')
    expect(link.attributes('href')).toBe('/cursos/c1')
    expect(link.text()).toBe('Java básico')
    expect(wrapper.text().toLowerCase()).toContain('listo')
    expect(wrapper.text()).toContain('42 de 42 piezas')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    expect(wrapper.text()).not.toContain('READY')
  })

  it('says errors and pending pieces in words', async () => {
    const { wrapper } = await open({ status: 'PAUSED', done: 8, failed: 2, pending: 3, total: 13 })

    expect(wrapper.get('[data-testid="result-failed-c1"]').text()).toContain('2 con error')
    expect(wrapper.text()).toContain('3 pendientes')
    expect(wrapper.text().toLowerCase()).toContain('pausa')
  })

  it('shows the calls and the cost for an adult', async () => {
    const { wrapper } = await open()

    expect(wrapper.text()).toContain('43 llamadas')
    expect(wrapper.get('[data-testid="result-cost-c1"]').text()).toContain('0,0612')
  })

  it('shows the calls but no amount at all when the amounts are hidden (a minor)', async () => {
    const { wrapper } = await open({ costUsd: undefined }, false)

    expect(wrapper.text()).toContain('43 llamadas')
    expect(wrapper.find('[data-testid="result-cost-c1"]').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/USD|€|\$/)
  })

  it('renders a hostile title as plain text and breaks a very long one instead of overflowing', async () => {
    const { wrapper } = await open({ title: '<img src=x onerror=alert(1)>' + 'x'.repeat(200) })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-testid="result-link-c1"]').element.closest('.result-card')).not.toBeNull()
  })

  it('keeps the test id of the card', async () => {
    const { wrapper } = await open()

    expect(wrapper.attributes('data-testid')).toBe('result-c1')
  })
})
