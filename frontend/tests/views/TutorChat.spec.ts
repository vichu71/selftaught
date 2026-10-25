import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), getTutorThread: vi.fn(), askTutor: vi.fn(), clearTutor: vi.fn(), setLessonDone: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const lesson = (over: object = {}) => ({
  id: 'p1', kind: 'LESSON', status: 'DONE', title: 'To be', generatedByAi: true, markdown: '# El verbo to be\n\nSe usa para ser y estar, con ejemplos.',
  sources: [], done: false, navigation: {}, ...over,
})

const message = (over: object = {}) => ({ id: 'm1', role: 'USER', status: 'OK', content: 'Una duda', createdAt: '2026-10-25T10:00:00Z', ...over })

const answerOf = (over: object = {}) => ({
  user: message({ id: 'u9', content: 'mi duda' }),
  tutor: message({ id: 't9', role: 'TUTOR', content: 'Respuesta con **negrita**', sources: [{ n: 1, page: 3 }] }),
  ...over,
})

const open = (user = adult) => mountApp(PieceView, { user, route: '/cursos/c1/piezas/p1' })

async function type(m: Awaited<ReturnType<typeof open>>, text: string) {
  await byId(m.wrapper, 'tutor-input').setValue(text)
}

async function send(m: Awaited<ReturnType<typeof open>>, text = 'mi duda') {
  await type(m, text)
  await byId(m.wrapper, 'tutor-send').trigger('click')
  await flushPromises()
}

// AC-1, AC-9, AC-10, AC-11, AC-14, AC-15: el chat con el tutor dentro de la lección
describe('Tutor chat', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue(lesson() as never)
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [] } as never)
    vi.mocked(api.askTutor).mockResolvedValue(answerOf() as never)
    vi.mocked(api.clearTutor).mockResolvedValue(undefined as never)
  })

  // ---------- dónde aparece ----------

  it('shows the chat under a lesson that is generated and loads its thread', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'tutor-chat').exists()).toBe(true)
    expect(api.getTutorThread).toHaveBeenCalledWith('c1', 'p1')
  })

  it('shows no chat for a lesson that is not generated yet, nor for exercises or exams', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p3', kind: 'LESSON', status: 'PENDING', title: 'x', generatedByAi: true, sources: [] } as never)
    const pending = await open()
    expect(byId(pending.wrapper, 'tutor-chat').exists()).toBe(false)

    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p4', kind: 'EXERCISES', status: 'DONE', title: 'x', generatedByAi: true, sources: [], exercises: [] } as never)
    const exercises = await open()
    expect(byId(exercises.wrapper, 'tutor-chat').exists()).toBe(false)

    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p5', kind: 'EXAM', status: 'DONE', title: 'x', generatedByAi: true, sources: [], questions: [] } as never)
    const exam = await open()
    expect(byId(exam.wrapper, 'tutor-chat').exists()).toBe(false)
    expect(api.getTutorThread).not.toHaveBeenCalled() // ninguna de las tres es una lección hecha
  })

  it('shows the earlier messages of the thread with the tutor ones rendered with format and its sources', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [
      message({ id: 'a', content: 'Mi duda anterior' }),
      message({ id: 'b', role: 'TUTOR', content: 'Una explicación con **negrita** y [1].', sources: [{ n: 1, page: 3 }, { n: 2 }] }),
    ] } as never)
    const m = await open()

    expect(byId(m.wrapper, 'tutor-msg-a').text()).toContain('Mi duda anterior')
    expect(byId(m.wrapper, 'tutor-msg-b').find('strong').text()).toBe('negrita')
    expect(byId(m.wrapper, 'tutor-sources-b').text()).toContain('Fuente 1')
    expect(byId(m.wrapper, 'tutor-sources-b').text()).toContain('3')
    expect(byId(m.wrapper, 'tutor-sources-b').text()).toContain('Fuente 2')
  })

  it('carries the generated-by-AI notice', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'tutor-ai-notice').text().toLowerCase()).toContain('generado por ia')
  })

  it('still lets the student ask if the thread could not be loaded, and says so', async () => {
    vi.mocked(api.getTutorThread).mockRejectedValue(new ApiError(500))
    const m = await open()

    expect(byId(m.wrapper, 'tutor-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'tutor-input').exists()).toBe(true)
  })

  it('does not break the lesson when the thread endpoint is not available at all', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue(undefined as never)
    const m = await open()

    expect(byId(m.wrapper, 'lesson').exists()).toBe(true)
  })

  // ---------- preguntar ----------

  it('sends the question and shows both messages, the answer with its format and sources, and clears the box', async () => {
    const m = await open()

    await send(m, 'mi duda')

    expect(api.askTutor).toHaveBeenCalledWith('c1', 'p1', 'mi duda')
    expect(byId(m.wrapper, 'tutor-msg-u9').text()).toContain('mi duda')
    expect(byId(m.wrapper, 'tutor-msg-t9').find('strong').text()).toBe('negrita')
    expect(byId(m.wrapper, 'tutor-sources-t9').text()).toContain('Fuente 1')
    expect((byId(m.wrapper, 'tutor-input').element as HTMLTextAreaElement).value).toBe('')
  })

  it('sends nothing when the box is empty or only spaces', async () => {
    const m = await open()

    await send(m, '   ')

    expect(api.askTutor).not.toHaveBeenCalled()
  })

  it('shows a thinking indicator and blocks a second send while waiting', async () => {
    let finish: (value: unknown) => void = () => undefined
    vi.mocked(api.askTutor).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const m = await open()

    await type(m, 'mi duda')
    await byId(m.wrapper, 'tutor-send').trigger('click')
    await byId(m.wrapper, 'tutor-send').trigger('click')

    expect(byId(m.wrapper, 'tutor-thinking').exists()).toBe(true)
    expect(byId(m.wrapper, 'tutor-thinking').attributes('role')).toBe('status')
    expect(byId(m.wrapper, 'tutor-send').attributes('disabled')).toBeDefined()
    finish(answerOf())
    await flushPromises()

    expect(api.askTutor).toHaveBeenCalledTimes(1)
    expect(byId(m.wrapper, 'tutor-thinking').exists()).toBe(false)
  })

  it('limits the question to a thousand characters and shows how many are used', async () => {
    const m = await open()

    await type(m, 'hola tutor')

    expect(byId(m.wrapper, 'tutor-input').attributes('maxlength')).toBe('1000')
    expect(byId(m.wrapper, 'tutor-count').text()).toContain('10')
    expect(byId(m.wrapper, 'tutor-count').text()).toContain('1000')
  })

  // ---------- errores amables ----------

  it.each([
    ['AI_USER_LIMIT', 'límite'],
    ['AI_BUDGET_BLOCKED', 'límite'],
    ['TUTOR_RATE_LIMITED', 'muchos'],
    ['TUTOR_BUSY', 'en marcha'],
    ['TUTOR_FAILED', 'no he podido responder'],
    ['AI_PROVIDER_UNAVAILABLE', 'no está disponible'],
  ])('explains the %s error in plain words, keeps the question in the box and adds no message', async (code, fragment) => {
    vi.mocked(api.askTutor).mockRejectedValue(new ApiError(429, code))
    const m = await open()

    await send(m, 'mi duda importante')

    expect(byId(m.wrapper, 'tutor-error').text().toLowerCase()).toContain(fragment)
    expect(m.wrapper.text()).not.toContain(code)
    expect((byId(m.wrapper, 'tutor-input').element as HTMLTextAreaElement).value).toBe('mi duda importante')
    expect(m.wrapper.findAll('[data-testid^="tutor-msg-"]')).toHaveLength(0)
  })

  it('explains a generic failure and lets the student try again', async () => {
    vi.mocked(api.askTutor).mockRejectedValueOnce(new ApiError(500)).mockResolvedValueOnce(answerOf() as never)
    const m = await open()

    await send(m, 'mi duda')
    expect(byId(m.wrapper, 'tutor-error').text().toLowerCase()).toContain('no se pudo')
    await byId(m.wrapper, 'tutor-send').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'tutor-error').exists()).toBe(false)
    expect(byId(m.wrapper, 'tutor-msg-t9').exists()).toBe(true)
  })

  it('says the lesson is not ready yet when the server answers 409', async () => {
    vi.mocked(api.askTutor).mockRejectedValue(new ApiError(409))
    const m = await open()

    await send(m)

    expect(byId(m.wrapper, 'tutor-error').text().toLowerCase()).toContain('todavía')
  })

  // ---------- bloqueos (AC-8) ----------

  it('shows a blocked question with the agreed message, marked as blocked', async () => {
    vi.mocked(api.askTutor).mockResolvedValue(answerOf({
      user: message({ id: 'u9', status: 'BLOCKED', content: 'una pregunta bloqueada' }),
      tutor: message({ id: 't9', role: 'TUTOR', status: 'BLOCKED', content: 'Contenido inapropiado. Consulta con tu adulto.' }),
    }) as never)
    const m = await open(minor)

    await send(m, 'una pregunta bloqueada')

    expect(byId(m.wrapper, 'tutor-msg-t9').text()).toContain('Contenido inapropiado. Consulta con tu adulto.')
    expect(byId(m.wrapper, 'tutor-msg-t9').attributes('data-status')).toBe('BLOCKED')
  })

  it('shows a filtered answer as the fixed text it was replaced by, marked as filtered', async () => {
    vi.mocked(api.askTutor).mockResolvedValue(answerOf({ tutor: message({ id: 't9', role: 'TUTOR', status: 'FILTERED', content: 'No puedo ayudarte con eso. Consulta con tu adulto.' }) }) as never)
    const m = await open(minor)

    await send(m)

    expect(byId(m.wrapper, 'tutor-msg-t9').attributes('data-status')).toBe('FILTERED')
  })

  // ---------- vaciar y menores (AC-9, AC-14) ----------

  it('lets an adult empty the conversation after a confirmation', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [message({ id: 'a' }), message({ id: 'b', role: 'TUTOR', content: 'x' })] } as never)
    const m = await open()

    await byId(m.wrapper, 'tutor-clear').trigger('click')
    expect(api.clearTutor).not.toHaveBeenCalled()
    await byId(m.wrapper, 'tutor-clear-confirm').trigger('click')
    await flushPromises()

    expect(api.clearTutor).toHaveBeenCalledWith('c1', 'p1')
    expect(m.wrapper.findAll('[data-testid^="tutor-msg-"]')).toHaveLength(0)
  })

  it('lets the adult change their mind', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [message({ id: 'a' })] } as never)
    const m = await open()

    await byId(m.wrapper, 'tutor-clear').trigger('click')
    await byId(m.wrapper, 'tutor-clear-cancel').trigger('click')

    expect(api.clearTutor).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'tutor-clear-confirm').exists()).toBe(false)
    expect(byId(m.wrapper, 'tutor-msg-a').exists()).toBe(true)
  })

  it('keeps the messages and explains when emptying fails', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [message({ id: 'a' })] } as never)
    vi.mocked(api.clearTutor).mockRejectedValue(new ApiError(500))
    const m = await open()

    await byId(m.wrapper, 'tutor-clear').trigger('click')
    await byId(m.wrapper, 'tutor-clear-confirm').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'tutor-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'tutor-msg-a').exists()).toBe(true)
  })

  it('offers no way to empty the conversation to a minor and tells them their adult tutor can see it', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: false, messages: [message({ id: 'a' })] } as never)
    const m = await open(minor)

    expect(byId(m.wrapper, 'tutor-clear').exists()).toBe(false)
    expect(byId(m.wrapper, 'tutor-minor-notice').text().toLowerCase()).toContain('adulto tutor')
  })

  it('shows the notice about the adult tutor only to a minor', async () => {
    const m = await open(adult)

    expect(byId(m.wrapper, 'tutor-minor-notice').exists()).toBe(false)
  })

  it('shows no empty button when there is nothing to empty', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'tutor-clear').exists()).toBe(false)
  })

  // ---------- seguridad ----------

  it('renders hostile text of the question, the answer and the sources as plain text', async () => {
    vi.mocked(api.getTutorThread).mockResolvedValue({ canClear: true, messages: [
      message({ id: 'a', content: '<img src=x onerror=alert(1)>' }),
      message({ id: 'b', role: 'TUTOR', content: '<script>alert(1)</script> y [clic](javascript:alert(1))' }),
    ] } as never)
    const m = await open()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(m.wrapper.find('a[href^="javascript"]').exists()).toBe(false)
    expect(byId(m.wrapper, 'tutor-msg-a').text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('loads the thread of the new lesson when the student moves to another one', async () => {
    const m = await open()
    vi.mocked(api.getPiece).mockResolvedValue(lesson({ id: 'p2', title: 'Saludos' }) as never)

    await m.router.push('/cursos/c1/piezas/p2')
    await flushPromises()

    expect(api.getTutorThread).toHaveBeenLastCalledWith('c1', 'p2')
  })
})
