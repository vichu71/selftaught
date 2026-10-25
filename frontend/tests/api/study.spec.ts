import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// AC-1 a AC-11: el cliente del estudio; todo lo que modifica lleva el token CSRF y los identificadores van codificados
describe('api client: study', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=token-csrf-123'
  })
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.unstubAllGlobals()
  })

  const last = () => fetchMock.mock.calls.at(-1) as [string, RequestInit & { headers: Record<string, string> }]

  it('reads the progress of every course and of one course with plain GET requests', async () => {
    fetchMock.mockResolvedValue(respond(200, []))

    await api.getStudyProgress()
    expect(last()[0]).toBe('/api/study/progress')
    await api.getStudy('c/1')

    expect(last()[0]).toBe('/api/courses/c%2F1/study')
    expect(last()[1].method ?? 'GET').toBe('GET')
    expect(last()[1].headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('marks a lesson with a PUT that carries the CSRF header and the explicit value', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.setLessonDone('c1', 'p 1', true)

    const [url, init] = last()
    expect(url).toBe('/api/courses/c1/pieces/p%201/done')
    expect(init.method).toBe('PUT')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(JSON.parse(init.body as string)).toEqual({ done: true })
  })

  it('sends an exercise answer and returns the solution that comes back', async () => {
    fetchMock.mockResolvedValue(respond(201, { attemptId: 'a1', solution: 'am', explanation: 'porque sí' }))

    const result = await api.sendExerciseAnswer('c1', 'p2', 1, 'mi respuesta')

    const [url, init] = last()
    expect(url).toBe('/api/courses/c1/pieces/p2/exercises/1/attempts')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(JSON.parse(init.body as string)).toEqual({ answer: 'mi respuesta' })
    expect(result).toEqual({ attemptId: 'a1', solution: 'am', explanation: 'porque sí' })
  })

  it('sets the student own result and asks for the AI correction on the attempt', async () => {
    fetchMock.mockResolvedValueOnce(respond(204)).mockResolvedValueOnce(respond(200, { verdict: 'PARTIAL', comment: 'Casi' }))

    await api.setExerciseResult('c1', 'p2', 0, 'a1', 'CORRECT')
    expect(last()[0]).toBe('/api/courses/c1/pieces/p2/exercises/0/attempts/a1/result')
    expect(last()[1].method).toBe('PUT')
    expect(JSON.parse(last()[1].body as string)).toEqual({ result: 'CORRECT' })

    const grade = await api.aiGrade('c1', 'p2', 0, 'a1')
    expect(last()[0]).toBe('/api/courses/c1/pieces/p2/exercises/0/attempts/a1/ai-grade')
    expect(last()[1].method).toBe('POST')
    expect(last()[1].body).toBeUndefined()
    expect(grade.verdict).toBe('PARTIAL')
  })

  it('starts an exam and hands it in with one answer per question, blanks included', async () => {
    fetchMock.mockResolvedValueOnce(respond(201, { attemptId: 'e1', questions: [] })).mockResolvedValueOnce(respond(200, { correct: 1, total: 2, percent: 50, passed: true, questions: [] }))

    await api.startExam('c1', 'p5')
    expect(last()[0]).toBe('/api/courses/c1/pieces/p5/exam/attempts')
    expect(last()[1].method).toBe('POST')

    const result = await api.submitExam('c1', 'p5', 'e1', [1, null])
    expect(last()[0]).toBe('/api/courses/c1/pieces/p5/exam/attempts/e1/submit')
    expect(JSON.parse(last()[1].body as string)).toEqual({ answers: [1, null] })
    expect(result.passed).toBe(true)
  })

  it('reads the history of a piece and an old exam attempt', async () => {
    fetchMock.mockResolvedValue(respond(200, { kind: 'EXAM', attempts: [] }))

    await api.getAttempts('c1', 'p5')
    expect(last()[0]).toBe('/api/courses/c1/pieces/p5/attempts')
    await api.getExamAttempt('c1', 'p5', 'e1')
    expect(last()[0]).toBe('/api/courses/c1/pieces/p5/exam/attempts/e1')
  })

  it('exposes the status and code of the errors the study endpoints answer', async () => {
    fetchMock.mockResolvedValue(respond(429, { code: 'STUDY_RATE_LIMITED', message: 'x' }))

    await expect(api.sendExerciseAnswer('c1', 'p2', 0, 'x')).rejects.toMatchObject({ status: 429, code: 'STUDY_RATE_LIMITED' })
    await expect(api.aiGrade('c1', 'p2', 0, 'a1')).rejects.toBeInstanceOf(ApiError)
  })
})
