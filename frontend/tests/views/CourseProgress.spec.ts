import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getCourse: vi.fn(), resumeCourse: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CourseStatusView from '@/views/CourseStatusView.vue'
import { adult, byId, mountApp } from '../helpers'

const sheet = { schemaVersion: 1, topic: 'Inglés', focus: 'General', startLevel: 'Desde cero', goal: 'Aprender', topicsCount: 2, examPerTopic: true, documentIds: [] }

const outline = [
  {
    id: 't1', title: 'Presentarse', summary: 'Saludos y presentaciones',
    chapters: [
      { id: 'c1', title: 'To be', objective: 'Usar el verbo ser', lesson: { id: 'p1', status: 'DONE' }, exercises: { id: 'p2', status: 'DONE' } },
      { id: 'c2', title: 'Saludos', objective: 'Saludar', lesson: { id: 'p3', status: 'PENDING' }, exercises: { id: 'p4', status: 'PENDING' } },
    ],
    exam: { id: 'p5', status: 'PENDING' },
  },
]

const detail = (over: object = {}) => ({
  id: 'c1', title: 'Inglés', status: 'GENERATING', createdAt: '2026-10-25T10:00:00Z', sheet, documents: [], generatedByAi: true,
  progress: { done: 2, total: 5 }, costUsd: 0.01, stopReason: null, outline, ...over,
})

// AC-14: pantalla del curso con temario, progreso, pausa y reanudar; sondeo cada 3 s mientras se genera
describe('CourseStatusView: progreso de la generación', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the outline with the state of every piece', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'map-list').text()).toContain('Presentarse')
    expect(byId(m.wrapper, 'map-item-p1').text().toLowerCase()).toContain('ahora') // la primera lección lista
    expect(byId(m.wrapper, 'map-item-p3').text().toLowerCase()).toContain('en cola')
    expect(byId(m.wrapper, 'map-item-p5').text().toLowerCase()).toContain('examen')
  })

  it('shows the progress as done of total', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'progress').text()).toContain('2')
    expect(byId(m.wrapper, 'progress').text()).toContain('5')
  })

  it('only pieces that are done are links, to their own page', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'map-link-p1').attributes('href')).toBe('/cursos/c1/piezas/p1')
    expect(byId(m.wrapper, 'map-link-p3').exists()).toBe(false)
  })

  it('shows a failed piece with a plain explanation, not a raw code', async () => {
    const failedOutline = JSON.parse(JSON.stringify(outline))
    failedOutline[0].chapters[1].lesson.status = 'FAILED'
    vi.mocked(api.getCourse).mockResolvedValue(detail({ outline: failedOutline, status: 'PAUSED', stopReason: 'CONTENT' }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'map-item-p3').text().toLowerCase()).toContain('error')
    expect(m.wrapper.text()).not.toContain('CONTENT')
  })

  it('renders hostile titles as plain text', async () => {
    const hostile = JSON.parse(JSON.stringify(outline))
    hostile[0].title = '<img src=x onerror=alert(1)>'
    vi.mocked(api.getCourse).mockResolvedValue(detail({ outline: hostile }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'map-list').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  it('tolerates a detail without outline (a course that is still waiting)', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PENDING_GENERATION', outline: [], progress: { done: 0, total: 0 } }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).toContain('en cola')
  })

  // ---------- sondeo ----------

  it('refreshes by itself every three seconds while the course is being generated', async () => {
    vi.useFakeTimers()
    vi.mocked(api.getCourse).mockResolvedValueOnce(detail() as never).mockResolvedValue(detail({ progress: { done: 4, total: 5 } }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    expect(byId(m.wrapper, 'progress').text()).toContain('2')

    await vi.advanceTimersByTimeAsync(3100)
    await flushPromises()

    expect(byId(m.wrapper, 'progress').text()).toContain('4')
    expect(api.getCourse).toHaveBeenCalledTimes(2)
  })

  it.each(['READY', 'PAUSED', 'FAILED'])('stops asking once the course is %s', async (status) => {
    vi.useFakeTimers()
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status }) as never)
    await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    const calls = vi.mocked(api.getCourse).mock.calls.length

    await vi.advanceTimersByTimeAsync(20000)

    expect(vi.mocked(api.getCourse).mock.calls.length).toBe(calls)
  })

  it('stops asking when the user leaves the page', async () => {
    vi.useFakeTimers()
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    m.wrapper.unmount()
    const calls = vi.mocked(api.getCourse).mock.calls.length

    await vi.advanceTimersByTimeAsync(20000)

    expect(vi.mocked(api.getCourse).mock.calls.length).toBe(calls)
  })

  // ---------- pausa y reanudar ----------

  it.each([
    ['BUDGET', 'límite'],
    ['PROVIDER', 'proveedor'],
    ['CONTENT', 'no se pudieron'],
  ])('explains a pause for %s in plain words and offers to resume', async (stopReason, fragment) => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PAUSED', stopReason }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'pause-notice').text().toLowerCase()).toContain(fragment)
    expect(byId(m.wrapper, 'pause-notice').text()).not.toContain(stopReason)
    expect(byId(m.wrapper, 'resume').exists()).toBe(true)
  })

  it('does not offer to resume a course that is not paused', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'resume').exists()).toBe(false)
    expect(byId(m.wrapper, 'pause-notice').exists()).toBe(false)
  })

  it('resuming calls the API once and starts asking for progress again', async () => {
    vi.useFakeTimers()
    vi.mocked(api.getCourse).mockResolvedValueOnce(detail({ status: 'PAUSED', stopReason: 'PROVIDER' }) as never).mockResolvedValue(detail() as never)
    vi.mocked(api.resumeCourse).mockResolvedValue(detail({ status: 'PENDING_GENERATION' }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    await byId(m.wrapper, 'resume').trigger('click')
    await byId(m.wrapper, 'resume').trigger('click')
    await flushPromises()
    const afterResume = vi.mocked(api.getCourse).mock.calls.length
    await vi.advanceTimersByTimeAsync(3100)

    expect(api.resumeCourse).toHaveBeenCalledTimes(1)
    expect(api.resumeCourse).toHaveBeenCalledWith('c1')
    expect(vi.mocked(api.getCourse).mock.calls.length).toBeGreaterThan(afterResume)
  })

  it('tells the user to wait a moment when resuming too soon', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PAUSED', stopReason: 'PROVIDER' }) as never)
    vi.mocked(api.resumeCourse).mockRejectedValue(new ApiError(429, 'RESUME_TOO_SOON'))
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    await byId(m.wrapper, 'resume').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'resume-error').text().toLowerCase()).toContain('espera')
    expect(m.wrapper.text()).not.toContain('RESUME_TOO_SOON')
  })

  it('shows the agreed message when the course failed because the topic is not suitable', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'FAILED', stopReason: 'TOPIC_NOT_ALLOWED', outline: [], progress: { done: 0, total: 0 } }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'blocked-message').text()).toContain('Contenido inapropiado. Consulta con tu adulto.')
    expect(byId(m.wrapper, 'resume').exists()).toBe(false)
  })
})

// F5-6: se nota que el curso trabaja (indicador animado y «hace N s») y, si espera por otro curso del mismo usuario, lo dice
describe('CourseStatusView: indicador de actividad', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it.each(['PENDING_GENERATION', 'GENERATING'])('shows an activity indicator while the course is %s', async (status) => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'activity').exists()).toBe(true)
    expect(byId(m.wrapper, 'activity').attributes('role')).toBe('status')
  })

  it.each(['READY', 'PAUSED', 'FAILED'])('shows no activity indicator once the course is %s', async (status) => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'activity').exists()).toBe(false)
  })

  it('says when it was last checked and counts the seconds, starting again with every refresh', async () => {
    vi.useFakeTimers()
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    expect(byId(m.wrapper, 'updated').text()).toContain('0 s')

    await vi.advanceTimersByTimeAsync(2100)
    expect(byId(m.wrapper, 'updated').text()).toContain('2 s')

    await vi.advanceTimersByTimeAsync(1100) // llega el sondeo de los 3 s y vuelve a cero
    await flushPromises()
    expect(byId(m.wrapper, 'updated').text()).toMatch(/\b[01] s/)
  })

  it('tells the user the course waits for another one of theirs that is being generated', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PENDING_GENERATION', outline: [], progress: { done: 0, total: 0 }, waitingForOther: true }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).toContain('esperando')
    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).toContain('en cola')
  })

  it('keeps the plain queue message when it is not waiting for another course', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PENDING_GENERATION', outline: [], progress: { done: 0, total: 0 } }) as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).not.toContain('esperando')
  })

  it('stops the clock when the user leaves the page', async () => {
    vi.useFakeTimers()
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    m.wrapper.unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
