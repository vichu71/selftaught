import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getResults: vi.fn(), getUsage: vi.fn(), me: vi.fn(), logout: vi.fn(), listCourses: vi.fn(), listStudents: vi.fn(), listMaterial: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import { createAppRouter } from '@/router'
import HomeView from '@/views/HomeView.vue'
import ResultsView from '@/views/ResultsView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const course = (over: object = {}) => ({
  id: 'c1', title: 'Java básico', status: 'READY', done: 42, failed: 0, pending: 0, total: 42, calls: 43, costUsd: 0.0612, createdAt: '2026-10-25T10:00:00Z', ...over,
})

const usage = { month: '2026-10', you: { calls: 70, spendUsd: 0.18, limitUsd: 10, limitReached: false }, budget: { percent: 1.8, level: 'OK' }, minors: [] }

// Pantalla de resultados: piezas hechas, falladas y pendientes y coste de cada curso, y el gasto del mes
describe('ResultsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getResults).mockResolvedValue({
      courses: [course(), course({ id: 'c2', title: 'Inglés', status: 'PAUSED', stopReason: 'CONTENT', done: 8, failed: 2, pending: 0, total: 10, costUsd: 0.01 })],
      totalCostUsd: 0.0712,
    } as never)
    vi.mocked(api.getUsage).mockResolvedValue(usage as never)
  })

  it('shows each course with its pieces done, failed and pending', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'result-c1').text()).toContain('Java básico')
    expect(byId(m.wrapper, 'result-c1').text()).toContain('42 de 42')
    expect(byId(m.wrapper, 'result-c2').text()).toContain('8 de 10')
    expect(byId(m.wrapper, 'result-failed-c2').text()).toContain('2')
  })

  it('says in plain words what state each course is in, never the raw code', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'result-c1').text().toLowerCase()).toContain('listo')
    expect(byId(m.wrapper, 'result-c2').text().toLowerCase()).toContain('pausa')
    expect(m.wrapper.text()).not.toContain('CONTENT')
    expect(m.wrapper.text()).not.toContain('READY')
  })

  it('shows the cost of every course and the total for an adult', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'result-cost-c1').text()).toContain('0,0612')
    expect(byId(m.wrapper, 'results-total').text()).toContain('0,0712')
  })

  it('links every course to its page', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'result-link-c1').attributes('href')).toBe('/cursos/c1')
  })

  it('shows the month spend against the personal limit for an adult', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(api.getUsage).toHaveBeenCalled()
    expect(byId(m.wrapper, 'month').text()).toContain('0,18')
    expect(byId(m.wrapper, 'month').text()).toContain('10')
  })

  it('warns when the personal limit has been reached', async () => {
    vi.mocked(api.getUsage).mockResolvedValue({ ...usage, you: { ...usage.you, spendUsd: 10, limitReached: true } } as never)
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'limit-reached').exists()).toBe(true)
  })

  it('shows a minor their courses and calls but no amounts', async () => {
    vi.mocked(api.getResults).mockResolvedValue({ courses: [{ ...course(), costUsd: undefined }] } as never)
    vi.mocked(api.getUsage).mockResolvedValue({ month: '2026-10', you: { calls: 70, limitReached: false } } as never)
    const m = await mountApp(ResultsView, { user: minor, route: '/resultados' })

    expect(byId(m.wrapper, 'result-c1').text()).toContain('42 de 42')
    expect(byId(m.wrapper, 'result-cost-c1').exists()).toBe(false)
    expect(byId(m.wrapper, 'results-total').exists()).toBe(false)
    expect(m.wrapper.text()).not.toContain('USD')
    expect(m.wrapper.text()).not.toContain('€')
  })

  it('says there is nothing yet when there are no courses', async () => {
    vi.mocked(api.getResults).mockResolvedValue({ courses: [], totalCostUsd: 0 } as never)
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'results-empty').exists()).toBe(true)
  })

  it('shows a friendly error when the results cannot be loaded', async () => {
    vi.mocked(api.getResults).mockRejectedValue(new ApiError(500))
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'results-error').exists()).toBe(true)
  })

  it('renders hostile course titles as plain text', async () => {
    vi.mocked(api.getResults).mockResolvedValue({ courses: [course({ title: '<img src=x onerror=alert(1)>' })], totalCostUsd: 1 } as never)
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'result-c1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  it('offers a way back home', async () => {
    const m = await mountApp(ResultsView, { user: adult, route: '/resultados' })

    expect(byId(m.wrapper, 'back-home').attributes('href')).toBe('/')
  })
})

describe('Results navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([])
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [], minors: [] } as never)
  })

  it('is reachable from the home page', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'results-link').attributes('href')).toBe('/resultados')
  })

  it('redirects an anonymous visitor to the login', async () => {
    setActivePinia(createPinia())
    vi.mocked(api.me).mockRejectedValue(new Error('401'))
    const router = createAppRouter(createMemoryHistory())

    await router.push('/resultados')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })
})
