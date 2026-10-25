import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), setLessonDone: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, mountApp } from '../helpers'

const lesson = (over: object = {}) => ({
  id: 'p1', kind: 'LESSON', status: 'DONE', title: 'To be', generatedByAi: true, markdown: '# El verbo to be\n\nSe usa para ser y estar, con ejemplos.',
  sources: [], done: false, navigation: { previous: 'p0', next: 'p2' }, ...over,
})

// AC-3: «Marcar como hecha» (desmarcable) y enlaces a la lección anterior y siguiente
describe('Lesson study controls', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue(lesson() as never)
    vi.mocked(api.setLessonDone).mockResolvedValue(undefined as never)
  })

  it('offers to mark the lesson as done', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'mark-done').text()).toContain('Marcar como hecha')
    expect(byId(m.wrapper, 'mark-done').attributes('aria-pressed')).toBe('false')
  })

  it('marks it done with one request and shows the new state', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    await byId(m.wrapper, 'mark-done').trigger('click')
    await flushPromises()

    expect(api.setLessonDone).toHaveBeenCalledWith('c1', 'p1', true)
    expect(byId(m.wrapper, 'mark-done').text().toLowerCase()).toContain('hecha')
    expect(byId(m.wrapper, 'mark-done').attributes('aria-pressed')).toBe('true')
  })

  it('shows a lesson that is already done as done and lets the student unmark it', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(lesson({ done: true }) as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })
    expect(byId(m.wrapper, 'mark-done').attributes('aria-pressed')).toBe('true')

    await byId(m.wrapper, 'mark-done').trigger('click')
    await flushPromises()

    expect(api.setLessonDone).toHaveBeenCalledWith('c1', 'p1', false)
    expect(byId(m.wrapper, 'mark-done').attributes('aria-pressed')).toBe('false')
  })

  it('ignores a second click while the first request is still in flight', async () => {
    let finish: () => void = () => undefined
    vi.mocked(api.setLessonDone).mockReturnValue(new Promise<void>((resolve) => { finish = resolve }) as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    await byId(m.wrapper, 'mark-done').trigger('click')
    await byId(m.wrapper, 'mark-done').trigger('click')
    finish()
    await flushPromises()

    expect(api.setLessonDone).toHaveBeenCalledTimes(1)
  })

  it('keeps the old state and explains the problem when the request fails', async () => {
    vi.mocked(api.setLessonDone).mockRejectedValue(new ApiError(500))
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    await byId(m.wrapper, 'mark-done').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'done-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'mark-done').attributes('aria-pressed')).toBe('false')
  })

  it('links to the previous and the next lesson of the course', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'nav-previous').attributes('href')).toBe('/cursos/c1/piezas/p0')
    expect(byId(m.wrapper, 'nav-next').attributes('href')).toBe('/cursos/c1/piezas/p2')
  })

  it('shows no link where there is no previous or next lesson', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(lesson({ navigation: {} }) as never)
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'nav-previous').exists()).toBe(false)
    expect(byId(m.wrapper, 'nav-next').exists()).toBe(false)
  })

  it('loads the new lesson when the student follows the next link', async () => {
    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })
    vi.mocked(api.getPiece).mockResolvedValue(lesson({ id: 'p2', title: 'Saludos', navigation: { previous: 'p1' } }) as never)

    await m.router.push('/cursos/c1/piezas/p2')
    await flushPromises()

    expect(api.getPiece).toHaveBeenLastCalledWith('c1', 'p2')
    expect(byId(m.wrapper, 'piece-title').text()).toContain('Saludos')
  })

  it('does not offer to mark a lesson that is not generated yet, nor exercises or exams', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p3', kind: 'LESSON', status: 'PENDING', title: 'x', generatedByAi: true, sources: [] } as never)
    const pending = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p3' })
    expect(byId(pending.wrapper, 'mark-done').exists()).toBe(false)

    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p4', kind: 'EXERCISES', status: 'DONE', title: 'x', generatedByAi: true, sources: [], exercises: [] } as never)
    const exercises = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p4' })
    expect(byId(exercises.wrapper, 'mark-done').exists()).toBe(false)
  })
})
