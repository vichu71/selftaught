import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, mountApp } from '../helpers'

const lesson = {
  id: 'p1', kind: 'LESSON', status: 'DONE', title: 'To be', generatedByAi: true,
  markdown: '# El verbo to be\n\nSe usa para **ser** y *estar*. Según el material [1].\n\n| Persona | Forma |\n|---|---|\n| I | am |',
  sources: [{ n: 1, page: 3 }, { n: 2, page: null }],
}

const exercises = {
  id: 'p2', kind: 'EXERCISES', status: 'DONE', title: 'To be', generatedByAi: true,
  exercises: [
    { difficulty: 'LOW', statement: 'Completa: I ___ happy', solution: 'am', explanation: 'Con I se usa am' },
    { difficulty: 'HIGH', statement: 'Traduce: ellos están', solution: 'they are', explanation: 'Plural' },
  ],
}

const exam = {
  id: 'p5', kind: 'EXAM', status: 'DONE', title: 'Presentarse', generatedByAi: true,
  questions: [{ text: '¿Cuál es correcta?', options: ['I is', 'I am', 'I are'], correct: 1, explanation: 'Con I va am' }],
}

// AC-11 y AC-13: lectura de una pieza, con el Markdown pintado sin HTML y el aviso de «generado por IA»
describe('PieceView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue(lesson as never)
  })

  it('loads the piece of the route inside its course', async () => {
    await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(api.getPiece).toHaveBeenCalledWith('c1', 'p1')
  })

  it('shows a lesson with its formatting', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'lesson').find('h1').text()).toBe('El verbo to be')
    expect(byId(m.wrapper, 'lesson').find('strong').text()).toBe('ser')
    expect(byId(m.wrapper, 'lesson').find('table').exists()).toBe(true)
  })

  it('lists the cited sources generically, with their page when there is one', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    const text = byId(m.wrapper, 'sources').text()
    expect(text).toContain('Fuente 1')
    expect(text).toContain('3')
    expect(text).toContain('Fuente 2')
  })

  it('carries the generated-by-AI notice and a way back to the course', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(m.wrapper.text().toLowerCase()).toContain('generado por ia')
    expect(byId(m.wrapper, 'back-course').attributes('href')).toBe('/cursos/c1')
  })

  it('never runs a hostile lesson', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ ...lesson, markdown: '# Hola\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n[clic](javascript:alert(1))' } as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('a[href^="javascript"]').exists()).toBe(false)
    expect(m.wrapper.html()).not.toMatch(/\son[a-z]+=/i)
  })

  it('shows exercises with their solution hidden until it is asked for', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(exercises as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p2' })

    expect(byId(m.wrapper, 'exercise-0').text()).toContain('Completa: I ___ happy')
    const details = byId(m.wrapper, 'exercise-0').find('details')
    expect(details.exists()).toBe(true)
    expect(details.attributes('open')).toBeUndefined()
    expect(byId(m.wrapper, 'exercise-1').text()).toContain('Traduce')
  })

  it('shows an exam with its options and the answers hidden until they are asked for', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(exam as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p5' })

    expect(byId(m.wrapper, 'question-0').text()).toContain('¿Cuál es correcta?')
    expect(byId(m.wrapper, 'question-0').findAll('li').map((li) => li.text())).toEqual(['I is', 'I am', 'I are'])
    expect(byId(m.wrapper, 'question-0').find('details').attributes('open')).toBeUndefined()
  })

  it('says a piece that is not done yet is still being prepared', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p3', kind: 'LESSON', status: 'PENDING', title: 'Saludos', generatedByAi: true, sources: [] } as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p3' })

    expect(byId(m.wrapper, 'piece-pending').exists()).toBe(true)
    expect(byId(m.wrapper, 'lesson').exists()).toBe(false)
  })

  it('shows a clear message when the piece does not exist or is not yours', async () => {
    vi.mocked(api.getPiece).mockRejectedValue(new ApiError(404, 'NOT_FOUND'))
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/otra' })

    expect(byId(m.wrapper, 'piece-not-found').exists()).toBe(true)
  })

  it('shows a generic error for any other failure', async () => {
    vi.mocked(api.getPiece).mockRejectedValue(new ApiError(500))
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'piece-error').exists()).toBe(true)
  })
})
