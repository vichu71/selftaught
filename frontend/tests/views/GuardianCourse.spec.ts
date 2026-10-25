import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getGuardianCourse: vi.fn(), getGuardianAttempts: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import GuardianCourseView from '@/views/GuardianCourseView.vue'
import { adult, byId, mountApp } from '../helpers'

const course = () => ({
  id: 'c1', title: 'Historia', status: 'READY',
  overview: {
    percent: 40, lessonsDone: 2, lessonsTotal: 4, examsPassed: 1, examsTotal: 2,
    topics: [
      { id: 't1', percent: 75, lessons: [{ pieceId: 'p1', status: 'DONE', done: true }, { pieceId: 'p2', status: 'DONE', done: false }], exam: { pieceId: 'e1', status: 'DONE', attempts: 2, best: 80, last: 40, passed: true } },
      { id: 't2', percent: 0, lessons: [{ pieceId: 'p3', status: 'PENDING', done: false }], exam: { pieceId: 'e2', status: 'PENDING', attempts: 0, passed: false } },
    ],
  },
})

const attempts = (over: object = {}) => ({
  truncated: false,
  pieces: [
    {
      pieceId: 'x1', kind: 'EXERCISES', title: 'Capítulo 1.1',
      exercises: [{ item: 0, statement: 'Primer ejercicio', attempts: [
        { answer: 'mi respuesta uno', selfResult: 'PARTIAL', aiResult: 'INCORRECT', aiComment: 'Revisa el segundo paso.', createdAt: '2026-10-25T10:00:00Z' },
        { answer: 'mi primera respuesta', createdAt: '2026-10-25T09:00:00Z' },
      ] }],
    },
    { pieceId: 'e1', kind: 'EXAM', title: 'Tema 1', exams: [{ submittedAt: '2026-10-25T11:00:00Z', correct: 4, total: 5, percent: 80, passed: true, failed: ['Pregunta 4'] }] },
  ],
  ...over,
})

const open = () => mountApp(GuardianCourseView, { user: adult, route: '/alumnos/s1/cursos/c1' })

// AC-3, AC-4, AC-11, AC-12: el curso de un alumno y sus intentos, en solo lectura
describe('Panel del curso de un alumno', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getGuardianCourse).mockResolvedValue(course() as never)
    vi.mocked(api.getGuardianAttempts).mockResolvedValue(attempts() as never)
  })

  it('loads the course and the attempts of the student and the course of the route', async () => {
    const m = await open()

    expect(api.getGuardianCourse).toHaveBeenCalledWith('s1', 'c1')
    expect(api.getGuardianAttempts).toHaveBeenCalledWith('s1', 'c1')
    expect(byId(m.wrapper, 'guardian-course-title').text()).toContain('Historia')
  })

  it('shows the progress of each topic with its lessons and the marks of its exam', async () => {
    const m = await open()

    const first = byId(m.wrapper, 'guardian-topic-0').text()
    expect(first).toContain('75')
    expect(first).toContain('1') // lecciones hechas
    expect(first).toContain('80') // mejor nota
    expect(first).toContain('40') // última nota
    expect(byId(m.wrapper, 'guardian-topic-1').text()).toContain('0')
  })

  it('links the lessons of the course to their conversation', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-lesson-link-p1').attributes('href')).toBe('/alumnos/s1/cursos/c1/lecciones/p1')
  })

  it('shows the exercise attempts with the statement, the answer and both verdicts', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'guardian-attempts').text()
    expect(text).toContain('Primer ejercicio')
    expect(text).toContain('mi respuesta uno')
    expect(text).toContain('mi primera respuesta')
    expect(text).toContain('Revisa el segundo paso.')
    expect(text.toLowerCase()).toContain('casi') // autocorrección PARTIAL
    expect(text.toLowerCase()).toContain('fallado') // veredicto de la IA INCORRECT
    expect(text).not.toContain('PARTIAL')
    expect(text).not.toContain('INCORRECT')
  })

  it('shows the exam attempts with the mark and the failed questions', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'guardian-attempts').text()
    expect(text).toContain('4')
    expect(text).toContain('5')
    expect(text).toContain('80')
    expect(text).toContain('Pregunta 4')
  })

  it('says when the list was cut', async () => {
    vi.mocked(api.getGuardianAttempts).mockResolvedValue(attempts({ truncated: true }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-truncated').exists()).toBe(true)
  })

  it('does not say it was cut when it was not', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-truncated').exists()).toBe(false)
  })

  it('says so when there are no attempts', async () => {
    vi.mocked(api.getGuardianAttempts).mockResolvedValue({ truncated: false, pieces: [] } as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-attempts-empty').exists()).toBe(true)
  })

  it('renders hostile text of an answer or a statement as plain text', async () => {
    vi.mocked(api.getGuardianAttempts).mockResolvedValue(attempts({ pieces: [{
      pieceId: 'x1', kind: 'EXERCISES', title: '<b>t</b>',
      exercises: [{ item: 0, statement: '<script>alert(1)</script>', attempts: [{ answer: '<img src=x onerror=alert(1)> [c](javascript:alert(1))', createdAt: '2026-10-25T10:00:00Z' }] }],
    }] }) as never)
    const m = await open()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(m.wrapper.find('a[href^="javascript"]').exists()).toBe(false)
    expect(byId(m.wrapper, 'guardian-attempts').text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('shows a not-found message when the course is not of that student', async () => {
    vi.mocked(api.getGuardianCourse).mockRejectedValue(new ApiError(404))
    vi.mocked(api.getGuardianAttempts).mockRejectedValue(new ApiError(404))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-not-found').exists()).toBe(true)
  })

  it('still shows the course if the attempts cannot be loaded, with an error that hides the code', async () => {
    vi.mocked(api.getGuardianAttempts).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-error').exists()).toBe(true)
    expect(m.wrapper.text()).not.toContain('INTERNAL')
    expect(byId(m.wrapper, 'guardian-topic-0').exists()).toBe(true)
  })

  it('has a way back to the student and nothing that writes', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-back').attributes('href')).toBe('/alumnos/s1')
    expect(m.wrapper.find('textarea').exists()).toBe(false)
    expect(m.wrapper.find('input').exists()).toBe(false)
  })
})
