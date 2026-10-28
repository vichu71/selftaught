import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getResults: vi.fn(), getUsage: vi.fn(), getUsageTasks: vi.fn(), me: vi.fn(), logout: vi.fn(), listCourses: vi.fn(), listStudents: vi.fn(), listMaterial: vi.fn() } }
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
const tasks = { month: '2026-10', tasks: [{ task: 'LESSON', calls: 40, spendUsd: 0.4 }, { task: 'TUTOR', calls: 25, spendUsd: 0.1 }, { task: 'EMBEDDING', calls: 5, spendUsd: 0 }] }
const open = (user = adult) => mountApp(ResultsView, { user, route: '/resultados' })

// Pantalla de resultados (F15): medidor del tope, tarjetas de cifras, cursos como tarjetas, gasto por tarea y menores
describe('ResultsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getResults).mockResolvedValue({
      courses: [course(), course({ id: 'c2', title: 'Inglés', status: 'PAUSED', stopReason: 'CONTENT', done: 8, failed: 2, pending: 0, total: 10, costUsd: 0.01 })],
      totalCostUsd: 0.0712,
    } as never)
    vi.mocked(api.getUsage).mockResolvedValue(usage as never)
    vi.mocked(api.getUsageTasks).mockResolvedValue(tasks as never)
  })

  // ---------- lo que ya existía (AC-11: sigue en verde) ----------

  it('shows each course with its pieces done, failed and pending', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'result-c1').text()).toContain('Java básico')
    expect(byId(m.wrapper, 'result-c1').text()).toContain('42 de 42')
    expect(byId(m.wrapper, 'result-c2').text()).toContain('8 de 10')
    expect(byId(m.wrapper, 'result-failed-c2').text()).toContain('2')
  })

  it('says in plain words what state each course is in, never the raw code', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'result-c1').text().toLowerCase()).toContain('listo')
    expect(byId(m.wrapper, 'result-c2').text().toLowerCase()).toContain('pausa')
    expect(m.wrapper.text()).not.toContain('CONTENT')
    expect(m.wrapper.text()).not.toContain('READY')
  })

  it('shows the cost of every course and the total for an adult', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'result-cost-c1').text()).toContain('0,0612')
    expect(byId(m.wrapper, 'results-total').text()).toContain('0,0712')
  })

  it('links every course to its page', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'result-link-c1').attributes('href')).toBe('/cursos/c1')
  })

  it('warns when the personal limit has been reached', async () => {
    vi.mocked(api.getUsage).mockResolvedValue({ ...usage, you: { ...usage.you, spendUsd: 10, limitReached: true } } as never)
    const m = await open()

    expect(byId(m.wrapper, 'limit-reached').exists()).toBe(true)
    expect(byId(m.wrapper, 'tope-state').text().toLowerCase()).toContain('tope alcanzado')
  })

  it('says there is nothing yet when there are no courses', async () => {
    vi.mocked(api.getResults).mockResolvedValue({ courses: [], totalCostUsd: 0 } as never)
    const m = await open()

    expect(byId(m.wrapper, 'results-empty').exists()).toBe(true)
  })

  it('shows a friendly error when the results cannot be loaded', async () => {
    vi.mocked(api.getResults).mockRejectedValue(new ApiError(500))
    const m = await open()

    expect(byId(m.wrapper, 'results-error').exists()).toBe(true)
  })

  it('renders hostile course titles as plain text', async () => {
    vi.mocked(api.getResults).mockResolvedValue({ courses: [course({ title: '<img src=x onerror=alert(1)>' })], totalCostUsd: 1 } as never)
    const m = await open()

    expect(byId(m.wrapper, 'result-c1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  // ---------- botón «Volver» (AC-1) ----------

  it('offers a way back home with the arrow button', async () => {
    const m = await open()

    const back = byId(m.wrapper, 'back-home')
    expect(back.attributes('href')).toBe('/')
    expect(back.classes()).toContain('back-link')
    expect(back.text()).toBe('Inicio')
    expect(back.attributes('aria-label')).toBe('Volver al inicio')
  })

  // ---------- tarjetas de cifras y medidor (AC-2, AC-4, D3, D4) ----------

  it('says in friendly words how many times the AI helped this month', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'results-stat-calls').text()).toContain('70')
    expect(byId(m.wrapper, 'results-stat-calls').text().toLowerCase()).toContain('veces')
    expect(m.wrapper.text()).not.toContain('llamadas a la IA')
  })

  it('shows the month spend and the number of ready courses in cards', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'results-stat-spend').text()).toContain('0,18')
    expect(byId(m.wrapper, 'results-stat-ready').text()).toContain('1')
  })

  it('draws the limit meter with the spend against the limit and the state in words', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'month').text()).toContain('0,18')
    expect(byId(m.wrapper, 'month').text()).toContain('10,00')
    expect(byId(m.wrapper, 'tope-state').text().toLowerCase()).toContain('vas bien')
    expect(byId(m.wrapper, 'month').find('[role="progressbar"]').exists()).toBe(true)
  })

  it('says it is close to the limit from 80 %', async () => {
    vi.mocked(api.getUsage).mockResolvedValue({ ...usage, you: { ...usage.you, spendUsd: 8.5 } } as never)
    const m = await open()

    expect(byId(m.wrapper, 'tope-state').text().toLowerCase()).toContain('cerca del tope')
  })

  it('says the AI has not helped yet when there are no calls and shows no spend by task', async () => {
    vi.mocked(api.getUsage).mockResolvedValue({ month: '2026-10', you: { calls: 0, spendUsd: 0, limitUsd: 10, limitReached: false }, minors: [] } as never)
    vi.mocked(api.getUsageTasks).mockResolvedValue({ month: '2026-10', tasks: [] } as never)
    const m = await open()

    expect(m.wrapper.text()).toContain('La IA todavía no te ha ayudado este mes')
    expect(byId(m.wrapper, 'results-tasks').exists()).toBe(false)
    expect(m.wrapper.html()).not.toContain('NaN')
  })

  // ---------- gasto por tarea (AC-6, D6) ----------

  it('lists what the AI helped with, in words and in USD for an adult', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'results-tasks').text()
    expect(text).toContain('Lecciones')
    expect(text).toContain('0,40')
    expect(text).toContain('Tutor')
    expect(text).toContain('Material')
    expect(text).not.toContain('LESSON')
    expect(text).not.toContain('EMBEDDING')
    expect(api.getUsageTasks).toHaveBeenCalled()
  })

  it('translates every task to Spanish', async () => {
    vi.mocked(api.getUsageTasks).mockResolvedValue({
      month: '2026-10',
      tasks: ['OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING', 'INTAKE', 'IMAGE'].map((task) => ({ task, calls: 1, spendUsd: 0.1 })),
    } as never)
    const m = await open()

    const text = byId(m.wrapper, 'results-tasks').text()
    for (const word of ['Temario', 'Lecciones', 'Ejercicios y exámenes', 'Tutor', 'Material', 'Asistente', 'Ilustraciones']) expect(text).toContain(word)
  })

  it('says «1 vez» in the singular and «N veces» in the plural', async () => {
    vi.mocked(api.getUsage).mockResolvedValue({ month: '2026-10', you: { calls: 1, limitReached: false } } as never)
    vi.mocked(api.getUsageTasks).mockResolvedValue({ month: '2026-10', tasks: [{ task: 'LESSON', calls: 1 }, { task: 'TUTOR', calls: 4 }] } as never)
    const m = await open(minor)

    expect(byId(m.wrapper, 'results-stat-calls').text()).toContain('1 vez')
    expect(byId(m.wrapper, 'results-stat-calls').text()).not.toContain('1 veces')
    expect(byId(m.wrapper, 'results-tasks').text()).toContain('1 vez')
    expect(byId(m.wrapper, 'results-tasks').text()).toContain('4 veces')
    expect(byId(m.wrapper, 'results-tasks').text()).not.toContain('1 veces')
  })

  // ---------- menores (AC-3, D3, D6) ----------

  describe('for a minor', () => {
    beforeEach(() => {
      vi.mocked(api.getResults).mockResolvedValue({ courses: [{ ...course(), costUsd: undefined }] } as never)
      vi.mocked(api.getUsage).mockResolvedValue({ month: '2026-10', you: { calls: 70, limitReached: false } } as never)
      vi.mocked(api.getUsageTasks).mockResolvedValue({ month: '2026-10', tasks: [{ task: 'LESSON', calls: 5 }, { task: 'TUTOR', calls: 2 }] } as never)
    })

    it('shows their courses and calls but no amounts anywhere on the screen', async () => {
      const m = await open(minor)

      expect(byId(m.wrapper, 'result-c1').text()).toContain('42 de 42')
      expect(byId(m.wrapper, 'result-cost-c1').exists()).toBe(false)
      expect(byId(m.wrapper, 'results-total').exists()).toBe(false)
      expect(byId(m.wrapper, 'results-stat-spend').exists()).toBe(false)
      expect(byId(m.wrapper, 'tope-state').exists()).toBe(false)
      expect(byId(m.wrapper, 'results-minors').exists()).toBe(false)
      expect(m.wrapper.text()).not.toMatch(/USD|€|\$|tope|Tope/)
      expect(m.wrapper.find('[data-testid="month"] [role="progressbar"]').exists()).toBe(false)
    })

    it('says how many times the AI helped and, per task, the times and never an amount', async () => {
      const m = await open(minor)

      expect(byId(m.wrapper, 'results-stat-calls').text()).toContain('70')
      const text = byId(m.wrapper, 'results-tasks').text()
      expect(text).toContain('Lecciones')
      expect(text).toContain('5')
      expect(text).not.toMatch(/USD|,\d\d/)
    })
  })

  // ---------- «Tus peques» (AC-7, D7) ----------

  describe('Your little ones', () => {
    const minors = [
      { id: 'm1', displayName: 'Hugo', calls: 12, spendUsd: 0.25, limitUsd: 2, limitReached: false },
      { id: 'm2', displayName: '<b>Nico</b>', calls: 3, spendUsd: 2, limitUsd: 2, limitReached: true },
    ]

    it('shows each minor with their times, their spend and their limit meter for an adult with minors', async () => {
      vi.mocked(api.getUsage).mockResolvedValue({ ...usage, minors } as never)
      const m = await open()

      const block = byId(m.wrapper, 'results-minors')
      expect(block.text()).toContain('Hugo')
      expect(block.text()).toContain('12')
      expect(block.text()).toContain('0,25')
      expect(block.text()).toContain('2,00')
      expect(block.text().toLowerCase()).toContain('vas bien')
      expect(block.text().toLowerCase()).toContain('tope alcanzado')
    })

    it('renders a minor name as plain text', async () => {
      vi.mocked(api.getUsage).mockResolvedValue({ ...usage, minors } as never)
      const m = await open()

      expect(byId(m.wrapper, 'results-minors').text()).toContain('<b>Nico</b>')
      expect(byId(m.wrapper, 'results-minors').find('b').exists()).toBe(false)
    })

    // F16 AC-4 (D4, F15-2): un botón a la actividad de cada menor, en el panel del tutor de F08
    it('offers «Ver actividad» for each minor, as a button-shaped link to the guardian panel', async () => {
      vi.mocked(api.getUsage).mockResolvedValue({ ...usage, minors } as never)
      const m = await open()

      for (const id of ['m1', 'm2']) {
        const link = byId(m.wrapper, `results-minor-activity-${id}`)
        expect(link.attributes('href')).toBe(`/alumnos/${id}`)
        expect(link.classes()).toContain('ui-button')
        expect(link.text()).toBe('Ver actividad')
      }
    })

    it('does not show the block to an adult without minors', async () => {
      const m = await open()

      expect(byId(m.wrapper, 'results-minors').exists()).toBe(false)
    })
  })

  // ---------- estados y fallos por separado (AC-9) ----------

  it('shows a loading message while the data arrives', async () => {
    vi.mocked(api.getResults).mockReturnValue(new Promise(() => {}) as never)
    vi.mocked(api.getUsage).mockReturnValue(new Promise(() => {}) as never)
    vi.mocked(api.getUsageTasks).mockReturnValue(new Promise(() => {}) as never)
    const m = await open()

    expect(byId(m.wrapper, 'results-loading').exists()).toBe(true)
  })

  it('keeps showing the courses when only the usage fails, with a message in its place', async () => {
    vi.mocked(api.getUsage).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const m = await open()

    expect(byId(m.wrapper, 'result-c1').exists()).toBe(true)
    expect(byId(m.wrapper, 'results-usage-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'results-usage-error').text()).not.toMatch(/500|INTERNAL/)
  })

  it('keeps showing the month when only the courses fail', async () => {
    vi.mocked(api.getResults).mockRejectedValue(new ApiError(500))
    const m = await open()

    expect(byId(m.wrapper, 'results-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'month').exists()).toBe(true)
  })

  it('keeps everything else when only the spend by task fails', async () => {
    vi.mocked(api.getUsageTasks).mockRejectedValue(new ApiError(500))
    const m = await open()

    expect(byId(m.wrapper, 'result-c1').exists()).toBe(true)
    expect(byId(m.wrapper, 'month').exists()).toBe(true)
    expect(byId(m.wrapper, 'results-tasks-error').exists()).toBe(true)
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
