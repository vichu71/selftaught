import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getAttempts: vi.fn(), getExamAttempt: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import { createAppRouter } from '@/router'
import AttemptsView from '@/views/AttemptsView.vue'
import { adult, byId, mountApp } from '../helpers'

const exerciseAttempts = { kind: 'EXERCISES', attempts: [
  { id: 'a2', itemIndex: 2, answer: 'segundo intento', selfResult: 'CORRECT', aiResult: 'PARTIAL', aiComment: 'Casi del todo.', createdAt: '2026-10-25T11:00:00Z' },
  { id: 'a1', itemIndex: 0, answer: 'primer intento', createdAt: '2026-10-25T10:00:00Z' },
] }

const examAttempts = { kind: 'EXAM', attempts: [
  { id: 'e2', correct: 5, total: 5, percent: 100, passed: true, submittedAt: '2026-10-25T11:00:00Z' },
  { id: 'e1', correct: 1, total: 5, percent: 20, passed: false, submittedAt: '2026-10-25T10:00:00Z' },
] }

const detail = { correct: 1, total: 5, percent: 20, passed: false, questions: [
  { text: 'Pregunta fallada', options: ['a', 'b', 'c'], chosen: 0, correct: 2, explanation: 'Porque es la c', ok: false },
  { text: 'Pregunta acertada', options: ['a', 'b', 'c'], chosen: 1, correct: 1, explanation: 'Porque es la b', ok: true },
] }

// AC-11: historial de intentos y «qué fallé»
describe('AttemptsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getAttempts).mockResolvedValue(exerciseAttempts as never)
    vi.mocked(api.getExamAttempt).mockResolvedValue(detail as never)
  })

  it('loads the attempts of the piece of the route', async () => {
    await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p2/intentos' })

    expect(api.getAttempts).toHaveBeenCalledWith('c1', 'p2')
  })

  it('lists the exercise attempts newest first with the answer and how it went', async () => {
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p2/intentos' })

    expect(m.wrapper.findAll('[data-testid^="attempt-"]').map((e) => e.attributes('data-testid'))).toEqual(['attempt-a2', 'attempt-a1'])
    expect(byId(m.wrapper, 'attempt-a2').text()).toContain('segundo intento')
    expect(byId(m.wrapper, 'attempt-a2').text()).toContain('Ejercicio 3') // itemIndex 2
    expect(byId(m.wrapper, 'attempt-a2').text().toLowerCase()).toContain('acertado')
    expect(byId(m.wrapper, 'attempt-a2').text()).toContain('Casi del todo.')
    expect(byId(m.wrapper, 'attempt-a1').text().toLowerCase()).toContain('sin valorar')
  })

  it('lists the exam attempts with their mark and whether they passed', async () => {
    vi.mocked(api.getAttempts).mockResolvedValue(examAttempts as never)
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p5/intentos' })

    expect(byId(m.wrapper, 'attempt-e2').text()).toContain('5 de 5')
    expect(byId(m.wrapper, 'attempt-e2').text().toLowerCase()).toContain('superado')
    expect(byId(m.wrapper, 'attempt-e1').text()).toContain('20')
    expect(byId(m.wrapper, 'attempt-e1').text().toLowerCase()).not.toContain('superado')
  })

  it('opens an old exam attempt to see what went wrong', async () => {
    vi.mocked(api.getAttempts).mockResolvedValue(examAttempts as never)
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p5/intentos' })

    await byId(m.wrapper, 'attempt-open-e1').trigger('click')
    await flushPromises()

    expect(api.getExamAttempt).toHaveBeenCalledWith('c1', 'p5', 'e1')
    expect(byId(m.wrapper, 'result-q-0').text()).toContain('Pregunta fallada')
    expect(byId(m.wrapper, 'result-q-0').text().toLowerCase()).toContain('incorrecta')
    expect(byId(m.wrapper, 'result-q-0').text()).toContain('Porque es la c')
  })

  it('says there is nothing yet when there are no attempts', async () => {
    vi.mocked(api.getAttempts).mockResolvedValue({ kind: 'EXAM', attempts: [] } as never)
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p5/intentos' })

    expect(byId(m.wrapper, 'attempts-empty').exists()).toBe(true)
  })

  it('shows a clear message when the piece does not exist or is not yours, and a generic one for any other failure', async () => {
    vi.mocked(api.getAttempts).mockRejectedValue(new ApiError(404, 'NOT_FOUND'))
    const missing = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/otra/intentos' })
    expect(byId(missing.wrapper, 'attempts-not-found').exists()).toBe(true)

    vi.mocked(api.getAttempts).mockRejectedValue(new ApiError(500))
    const broken = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p2/intentos' })
    expect(byId(broken.wrapper, 'attempts-error').exists()).toBe(true)
  })

  it('renders hostile answers as plain text', async () => {
    vi.mocked(api.getAttempts).mockResolvedValue({ kind: 'EXERCISES', attempts: [{ id: 'a1', itemIndex: 0, answer: '<img src=x onerror=alert(1)>', createdAt: '2026-10-25T10:00:00Z' }] } as never)
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p2/intentos' })

    expect(byId(m.wrapper, 'attempt-a1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  it('offers a way back to the piece', async () => {
    const m = await mountApp(AttemptsView, { user: adult, route: '/cursos/c1/piezas/p2/intentos' })

    expect(byId(m.wrapper, 'back-piece').attributes('href')).toBe('/cursos/c1/piezas/p2')
  })

  it('is protected: an anonymous visitor goes to the login', async () => {
    setActivePinia(createPinia())
    vi.mocked(api.me).mockRejectedValue(new Error('401'))
    const router = createAppRouter(createMemoryHistory())

    await router.push('/cursos/c1/piezas/p2/intentos')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })
})
