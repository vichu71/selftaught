import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), startExam: vi.fn(), submitExam: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, mountApp } from '../helpers'

const piece = {
  id: 'p5', kind: 'EXAM', status: 'DONE', title: 'Presentarse', generatedByAi: true, sources: [],
  questions: [{ text: 'q1', options: ['a', 'b', 'c'] }, { text: 'q2', options: ['a', 'b', 'c'] }, { text: 'q3', options: ['a', 'b', 'c'] }],
}

const started = { attemptId: 'e1', questions: [
  { text: '¿Cuál es correcta?', options: ['I is', 'I am', 'I are'] },
  { text: '¿Y esta?', options: ['you is', 'you are', 'you am'] },
  { text: '¿Y la última?', options: ['he is', 'he are', 'he am'] },
] }

const result = (over: object = {}) => ({
  correct: 2, total: 3, percent: 67, passed: true,
  questions: [
    { text: '¿Cuál es correcta?', options: ['I is', 'I am', 'I are'], chosen: 1, correct: 1, explanation: 'Con I va am', ok: true },
    { text: '¿Y esta?', options: ['you is', 'you are', 'you am'], chosen: 0, correct: 1, explanation: 'Con you va are', ok: false },
    { text: '¿Y la última?', options: ['he is', 'he are', 'he am'], chosen: null, correct: 0, explanation: 'Con he va is', ok: false },
  ], ...over,
})

const open = () => mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p5' })

async function begin(m: Awaited<ReturnType<typeof open>>) {
  await byId(m.wrapper, 'start-exam').trigger('click')
  await flushPromises()
}
const pick = (m: Awaited<ReturnType<typeof open>>, q: number, o: number) => byId(m.wrapper, `option-${q}-${o}`).setValue(true)

// AC-8, AC-9, AC-10 y AC-11: hacer el examen, entregarlo, ver el resultado y repetirlo
describe('Exam taking', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue(piece as never)
    vi.mocked(api.startExam).mockResolvedValue(started as never)
    vi.mocked(api.submitExam).mockResolvedValue(result() as never)
  })

  it('shows the exam as a thing to start, with no questions and no answers yet', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'start-exam').exists()).toBe(true)
    expect(byId(m.wrapper, 'question-0').exists()).toBe(false)
    expect(m.wrapper.text()).not.toContain('Con I va am')
  })

  it('starts the exam and shows its questions with one option to choose each', async () => {
    const m = await open()
    await begin(m)

    expect(api.startExam).toHaveBeenCalledWith('c1', 'p5')
    expect(byId(m.wrapper, 'question-0').text()).toContain('¿Cuál es correcta?')
    expect(byId(m.wrapper, 'question-0').findAll('input[type="radio"]')).toHaveLength(3)
    expect(byId(m.wrapper, 'question-0').text()).toContain('I are')
    expect(byId(m.wrapper, 'start-exam').exists()).toBe(false)
  })

  it('hands in the chosen options, one per question, and shows the mark', async () => {
    const m = await open()
    await begin(m)
    await pick(m, 0, 1)
    await pick(m, 1, 0)
    await pick(m, 2, 2)

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(api.submitExam).toHaveBeenCalledWith('c1', 'p5', 'e1', [1, 0, 2])
    expect(byId(m.wrapper, 'exam-score').text()).toContain('2 de 3')
    expect(byId(m.wrapper, 'exam-score').text()).toContain('67')
    expect(byId(m.wrapper, 'exam-passed').text().toLowerCase()).toContain('superado')
  })

  it('warns about the questions left blank and only hands in after a second click', async () => {
    const m = await open()
    await begin(m)
    await pick(m, 0, 1)

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()
    expect(api.submitExam).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'unanswered-warning').text()).toContain('2')

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(api.submitExam).toHaveBeenCalledWith('c1', 'p5', 'e1', [1, null, null])
  })

  it('hands in only once even if the button is pressed twice', async () => {
    let finish: (value: unknown) => void = () => undefined
    vi.mocked(api.submitExam).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 1); await pick(m, 2, 0)

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await byId(m.wrapper, 'submit-exam').trigger('click')
    finish(result())
    await flushPromises()

    expect(api.submitExam).toHaveBeenCalledTimes(1)
  })

  it('shows every question with the chosen option, the correct one and the explanation after handing in', async () => {
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 0); await pick(m, 2, 2)
    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    const wrong = byId(m.wrapper, 'result-q-1').text()
    expect(byId(m.wrapper, 'result-q-0').text()).toContain('Con I va am')
    expect(wrong).toContain('you is') // lo que elegí
    expect(wrong).toContain('you are') // la correcta
    expect(wrong).toContain('Con you va are')
    expect(byId(m.wrapper, 'result-q-0').text().toLowerCase()).toContain('correcta')
    expect(wrong.toLowerCase()).toContain('incorrecta')
    expect(byId(m.wrapper, 'result-q-2').text().toLowerCase()).toContain('sin contestar')
  })

  it('encourages the student to review when the topic is not passed', async () => {
    vi.mocked(api.submitExam).mockResolvedValue(result({ correct: 1, percent: 33, passed: false }) as never)
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 0); await pick(m, 2, 2)
    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exam-passed').text().toLowerCase()).toContain('repasa')
    expect(byId(m.wrapper, 'exam-passed').text().toLowerCase()).not.toContain('superado')
  })

  it('lets the student repeat the exam with a new attempt and no option chosen', async () => {
    vi.mocked(api.startExam).mockResolvedValueOnce(started as never).mockResolvedValueOnce({ ...started, attemptId: 'e2' } as never)
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 0); await pick(m, 2, 2)
    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    await byId(m.wrapper, 'retry-exam').trigger('click')
    await flushPromises()

    expect(api.startExam).toHaveBeenCalledTimes(2)
    expect(byId(m.wrapper, 'exam-score').exists()).toBe(false)
    expect((byId(m.wrapper, 'option-0-1').element as HTMLInputElement).checked).toBe(false)
  })

  it('links to the history of attempts', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'history-link').attributes('href')).toBe('/cursos/c1/piezas/p5/intentos')
  })

  // ---------- errores ----------

  it('keeps the chosen options and lets the student try again when handing in fails', async () => {
    vi.mocked(api.submitExam).mockRejectedValueOnce(new ApiError(500))
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 0); await pick(m, 2, 2)

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exam-error').exists()).toBe(true)
    expect((byId(m.wrapper, 'option-0-1').element as HTMLInputElement).checked).toBe(true)
    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()
    expect(byId(m.wrapper, 'exam-score').exists()).toBe(true)
  })

  it('explains a rate limit without showing the raw code', async () => {
    vi.mocked(api.submitExam).mockRejectedValue(new ApiError(429, 'STUDY_RATE_LIMITED'))
    const m = await open()
    await begin(m)
    await pick(m, 0, 1); await pick(m, 1, 0); await pick(m, 2, 2)

    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'exam-error').text().toLowerCase()).toContain('muchos')
    expect(m.wrapper.text()).not.toContain('STUDY_RATE_LIMITED')
  })

  it.each([
    [409, undefined, 'todavía'],
    [500, undefined, 'no se pudo'],
  ])('explains a %s when starting the exam', async (status, code, fragment) => {
    vi.mocked(api.startExam).mockRejectedValue(new ApiError(status as number, code))
    const m = await open()

    await begin(m)

    expect(byId(m.wrapper, 'exam-error').text().toLowerCase()).toContain(fragment)
    expect(byId(m.wrapper, 'start-exam').exists()).toBe(true)
  })

  it('renders hostile questions, options and explanations as plain text', async () => {
    vi.mocked(api.startExam).mockResolvedValue({ attemptId: 'e1', questions: [{ text: '<img src=x onerror=alert(1)>', options: ['<script>x</script>', 'b', 'c'] }] } as never)
    vi.mocked(api.submitExam).mockResolvedValue(result({ questions: [{ text: '<img src=x onerror=alert(1)>', options: ['<script>x</script>', 'b', 'c'], chosen: 0, correct: 1, explanation: '<iframe></iframe>', ok: false }] }) as never)
    const m = await open()
    await begin(m)
    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)

    await pick(m, 0, 0)
    await byId(m.wrapper, 'submit-exam').trigger('click')
    await flushPromises()

    expect(m.wrapper.find('iframe').exists()).toBe(false)
    expect(m.wrapper.find('img').exists()).toBe(false)
  })
})
