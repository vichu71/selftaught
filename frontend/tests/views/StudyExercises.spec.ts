import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), sendExerciseAnswer: vi.fn(), setExerciseResult: vi.fn(), aiGrade: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, mountApp } from '../helpers'

const piece = {
  id: 'p2', kind: 'EXERCISES', status: 'DONE', title: 'To be', generatedByAi: true, sources: [],
  exercises: [
    { difficulty: 'LOW', statement: 'Completa: I ___ happy' },
    { difficulty: 'HIGH', statement: 'Traduce: ellos están' },
  ],
}

const open = () => mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p2' })

async function sent(m: Awaited<ReturnType<typeof open>>, i = 0, text = 'am') {
  await byId(m.wrapper, `answer-${i}`).setValue(text)
  await byId(m.wrapper, `send-${i}`).trigger('click')
  await flushPromises()
}

// AC-5, AC-6, AC-7 y AC-13: practicar un ejercicio, ver la solución solo después de responder y autocorregirse o pedir la corrección de la IA
describe('Exercises practice', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue(piece as never)
    vi.mocked(api.sendExerciseAnswer).mockResolvedValue({ attemptId: 'a1', solution: 'am', explanation: 'Con I se usa am' } as never)
    vi.mocked(api.setExerciseResult).mockResolvedValue(undefined as never)
    vi.mocked(api.aiGrade).mockResolvedValue({ verdict: 'PARTIAL', comment: 'Vas bien, pero falta un detalle.' } as never)
  })

  it('shows every exercise with a box to answer and no solution', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'exercise-0').text()).toContain('Completa: I ___ happy')
    expect(byId(m.wrapper, 'exercise-1').text()).toContain('Traduce: ellos están')
    expect(byId(m.wrapper, 'answer-0').exists()).toBe(true)
    expect(byId(m.wrapper, 'solution-0').exists()).toBe(false)
    expect(m.wrapper.find('details').exists()).toBe(false)
  })

  it('does not send an empty answer and says why', async () => {
    const m = await open()

    await byId(m.wrapper, 'send-0').trigger('click')
    await flushPromises()

    expect(api.sendExerciseAnswer).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'exercise-error-0').exists()).toBe(true)
  })

  it('sends the answer once and then shows the solution and the explanation', async () => {
    const m = await open()

    await byId(m.wrapper, 'answer-0').setValue('am')
    await byId(m.wrapper, 'send-0').trigger('click')
    await byId(m.wrapper, 'send-0').trigger('click')
    await flushPromises()

    expect(api.sendExerciseAnswer).toHaveBeenCalledTimes(1)
    expect(api.sendExerciseAnswer).toHaveBeenCalledWith('c1', 'p2', 0, 'am')
    expect(byId(m.wrapper, 'solution-0').text()).toContain('am')
    expect(byId(m.wrapper, 'solution-0').text()).toContain('Con I se usa am')
    expect(byId(m.wrapper, 'solution-1').exists()).toBe(false) // el otro ejercicio sigue sin respuesta
  })

  it('lets the student say how it went and marks the chosen button', async () => {
    const m = await open()
    await sent(m)

    await byId(m.wrapper, 'result-0-PARTIAL').trigger('click')
    await flushPromises()

    expect(api.setExerciseResult).toHaveBeenCalledWith('c1', 'p2', 0, 'a1', 'PARTIAL')
    expect(byId(m.wrapper, 'result-0-PARTIAL').attributes('aria-pressed')).toBe('true')
    expect(byId(m.wrapper, 'result-0-CORRECT').attributes('aria-pressed')).toBe('false')
  })

  it('keeps the old mark and explains when saving how it went fails', async () => {
    vi.mocked(api.setExerciseResult).mockRejectedValue(new ApiError(500))
    const m = await open()
    await sent(m)

    await byId(m.wrapper, 'result-0-CORRECT').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exercise-error-0').exists()).toBe(true)
    expect(byId(m.wrapper, 'result-0-CORRECT').attributes('aria-pressed')).toBe('false')
  })

  it('lets the student try the same exercise again with a new attempt', async () => {
    vi.mocked(api.sendExerciseAnswer).mockResolvedValueOnce({ attemptId: 'a1', solution: 'am', explanation: 'x' } as never).mockResolvedValueOnce({ attemptId: 'a2', solution: 'am', explanation: 'x' } as never)
    const m = await open()
    await sent(m, 0, 'primer intento')

    await byId(m.wrapper, 'retry-0').trigger('click')
    expect(byId(m.wrapper, 'solution-0').exists()).toBe(false)
    await sent(m, 0, 'segundo intento')

    expect(api.sendExerciseAnswer).toHaveBeenCalledTimes(2)
    expect(api.sendExerciseAnswer).toHaveBeenLastCalledWith('c1', 'p2', 0, 'segundo intento')
  })

  it('links to the history of attempts of the piece', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'history-link').attributes('href')).toBe('/cursos/c1/piezas/p2/intentos')
  })

  // ---------- corregir con IA ----------

  it('offers the AI correction only after the answer is sent', async () => {
    const m = await open()
    expect(byId(m.wrapper, 'ai-grade-0').exists()).toBe(false)

    await sent(m)

    expect(byId(m.wrapper, 'ai-grade-0').exists()).toBe(true)
  })

  it('shows the verdict and the comment of the AI with a notice that it can be wrong', async () => {
    const m = await open()
    await sent(m)

    await byId(m.wrapper, 'ai-grade-0').trigger('click')
    await flushPromises()

    expect(api.aiGrade).toHaveBeenCalledWith('c1', 'p2', 0, 'a1')
    expect(byId(m.wrapper, 'ai-verdict-0').text()).toContain('Casi')
    expect(byId(m.wrapper, 'ai-verdict-0').text()).toContain('Vas bien, pero falta un detalle.')
    expect(byId(m.wrapper, 'ai-verdict-0').text().toLowerCase()).toContain('equivocar')
  })

  it('asks for the AI correction only once per attempt', async () => {
    const m = await open()
    await sent(m)

    await byId(m.wrapper, 'ai-grade-0').trigger('click')
    await flushPromises()

    expect(api.aiGrade).toHaveBeenCalledTimes(1)
    expect(byId(m.wrapper, 'ai-grade-0').exists()).toBe(false) // ya no se ofrece: una corrección por intento
  })

  it.each([
    ['AI_GRADING_LIMIT', 'hoy'],
    ['AI_USER_LIMIT', 'límite'],
    ['GRADING_FAILED', 'autocorregir'],
    ['GRADING_BUSY', 'en marcha'],
    ['AI_PROVIDER_UNAVAILABLE', 'no está disponible'],
  ])('explains the %s error in plain words and keeps the answer and the solution', async (code, fragment) => {
    vi.mocked(api.aiGrade).mockRejectedValue(new ApiError(429, code))
    const m = await open()
    await sent(m, 0, 'mi respuesta')

    await byId(m.wrapper, 'ai-grade-0').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exercise-error-0').text().toLowerCase()).toContain(fragment)
    expect(m.wrapper.text()).not.toContain(code)
    expect((byId(m.wrapper, 'answer-0').element as HTMLTextAreaElement).value).toBe('mi respuesta')
    expect(byId(m.wrapper, 'solution-0').exists()).toBe(true)
    expect(byId(m.wrapper, 'result-0-CORRECT').exists()).toBe(true) // sigue pudiendo autocorregirse
  })

  it('shows the agreed message when the answer of a minor is not allowed', async () => {
    vi.mocked(api.aiGrade).mockRejectedValue(new ApiError(422, 'ANSWER_NOT_ALLOWED'))
    const m = await open()
    await sent(m)

    await byId(m.wrapper, 'ai-grade-0').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exercise-error-0').text()).toContain('Contenido inapropiado. Consulta con tu adulto.')
  })

  it('explains a rate limit when sending and keeps what was written', async () => {
    vi.mocked(api.sendExerciseAnswer).mockRejectedValue(new ApiError(429, 'STUDY_RATE_LIMITED'))
    const m = await open()

    await sent(m, 0, 'mi respuesta')

    expect(byId(m.wrapper, 'exercise-error-0').text().toLowerCase()).toContain('muchos')
    expect((byId(m.wrapper, 'answer-0').element as HTMLTextAreaElement).value).toBe('mi respuesta')
    expect(byId(m.wrapper, 'solution-0').exists()).toBe(false)
  })

  it('renders hostile text of the statement, the solution and the comment as plain text', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ ...piece, exercises: [{ difficulty: 'LOW', statement: '<img src=x onerror=alert(1)>' }] } as never)
    vi.mocked(api.sendExerciseAnswer).mockResolvedValue({ attemptId: 'a1', solution: '<script>alert(1)</script>', explanation: '<b>x</b>' } as never)
    vi.mocked(api.aiGrade).mockResolvedValue({ verdict: 'CORRECT', comment: '<iframe src=x></iframe>' } as never)
    const m = await open()
    await sent(m)
    await byId(m.wrapper, 'ai-grade-0').trigger('click')
    await flushPromises()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(m.wrapper.find('iframe').exists()).toBe(false)
    expect(m.wrapper.find('b').exists()).toBe(false)
    expect(byId(m.wrapper, 'solution-0').text()).toContain('<script>alert(1)</script>')
  })
})
