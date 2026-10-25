import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { startIntake: vi.fn(), createCourse: vi.fn(), listMaterial: vi.fn(), uploadMaterial: vi.fn(), me: vi.fn(), logout: vi.fn() },
  }
})
import { ApiError, api } from '@/api/client'
import NewCourseView from '@/views/NewCourseView.vue'
import { adult, byId, mountApp } from '../helpers'

type Mounted = Awaited<ReturnType<typeof mountApp>>

const q1 = {
  id: 'q1', text: '¿En qué quieres centrarte?',
  options: [
    { id: 'o1', label: 'Gramática', isDefault: true },
    { id: 'o2', label: 'Conversación', isDefault: false },
    { id: 'auto', label: 'Como tú veas', isDefault: false },
  ],
}
const q2 = {
  id: 'q2', text: '¿Cuántos temas?',
  options: [
    { id: 'o1', label: 'Pocos', isDefault: false },
    { id: 'o2', label: 'Normal', isDefault: true },
    { id: 'auto', label: 'Como tú veas', isDefault: false },
  ],
}
const doc = (id: string, name: string, status = 'READY') => ({
  id, name, mediaType: 'PDF', sizeBytes: 100, status, chunkCount: 1, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true,
})

async function typeTopic(m: Mounted, topic: string) {
  await byId(m.wrapper, 'topic-input').setValue(topic)
  await byId(m.wrapper, 'topic-next').trigger('click')
  await flushPromises()
}

/** Pasa el paso 1 y el 2 (sin material) y deja el asistente en la primera pregunta. */
async function goToQuestions(m: Mounted, topic = 'Inglés de 2º de la ESO') {
  await typeTopic(m, topic)
  await byId(m.wrapper, 'material-no').trigger('click')
  await byId(m.wrapper, 'material-next').trigger('click')
  await flushPromises()
}

describe('NewCourseView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [doc('d1', 'apuntes.pdf'), doc('d2', 'resumen.docx', 'PROCESSING')], minors: [] } as never)
    vi.mocked(api.startIntake).mockResolvedValue({ intakeId: 'i1', questions: [q1, q2] } as never)
    vi.mocked(api.createCourse).mockResolvedValue({ id: 'c1', title: 'Inglés', status: 'PENDING_GENERATION' } as never)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  // ---------- paso 1: el tema ----------

  it('asks the question in plain words and starts with an empty box', async () => {
    const m = await mountApp(NewCourseView, { user: adult })

    expect(m.wrapper.text()).toContain('¿Qué quieres estudiar?')
    expect((byId(m.wrapper, 'topic-input').element as HTMLInputElement).value).toBe('')
  })

  it('rejects a topic that is too short or too long without leaving the step', async () => {
    const m = await mountApp(NewCourseView, { user: adult })

    await typeTopic(m, 'ab')
    expect(byId(m.wrapper, 'topic-error').exists()).toBe(true)
    await typeTopic(m, 'x'.repeat(201))
    expect(byId(m.wrapper, 'topic-error').exists()).toBe(true)
    expect(byId(m.wrapper, 'material-no').exists()).toBe(false)
    expect(api.startIntake).not.toHaveBeenCalled()
  })

  it('accepts a valid topic, trims it and moves to the material question', async () => {
    const m = await mountApp(NewCourseView, { user: adult })

    await typeTopic(m, '   Inglés de 2º de la ESO   ')

    expect(byId(m.wrapper, 'topic-error').exists()).toBe(false)
    expect(m.wrapper.text()).toContain('¿Tienes información que aportar?')
  })

  // ---------- paso 2: material ----------

  it('with "No" it asks the assistant without documents', async () => {
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m)

    expect(api.startIntake).toHaveBeenCalledWith('Inglés de 2º de la ESO', [])
  })

  it('with "Sí" it lists the ready documents and disables those still being processed', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await typeTopic(m, 'Inglés')

    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'doc-option-d1').attributes('disabled')).toBeUndefined()
    expect(byId(m.wrapper, 'doc-option-d2').attributes('disabled')).toBeDefined()
    expect(m.wrapper.text()).toContain('apuntes.pdf')
  })

  it('sends the chosen documents to the assistant', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await typeTopic(m, 'Inglés')
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    await byId(m.wrapper, 'doc-option-d1').setValue(true)
    await byId(m.wrapper, 'material-next').trigger('click')
    await flushPromises()

    expect(api.startIntake).toHaveBeenCalledWith('Inglés', ['d1'])
  })

  it('asks to choose at least one document when the answer is "Sí"', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await typeTopic(m, 'Inglés')
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    await byId(m.wrapper, 'material-next').trigger('click')

    expect(byId(m.wrapper, 'docs-error').exists()).toBe(true)
    expect(api.startIntake).not.toHaveBeenCalled()
  })

  it('does not allow more than five documents', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue({ own: Array.from({ length: 6 }, (_, i) => doc(`d${i}`, `doc${i}.pdf`)), minors: [] } as never)
    const m = await mountApp(NewCourseView, { user: adult })
    await typeTopic(m, 'Inglés')
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    for (let i = 0; i < 6; i++) await byId(m.wrapper, `doc-option-d${i}`).setValue(true)
    await byId(m.wrapper, 'material-next').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'docs-error').exists()).toBe(true)
    expect(api.startIntake).not.toHaveBeenCalled()
  })

  it('shows a waiting message while the assistant is thinking and blocks a second request', async () => {
    let finish: (v: unknown) => void = () => undefined
    vi.mocked(api.startIntake).mockReturnValue(new Promise((resolve) => (finish = resolve)) as never)
    const m = await mountApp(NewCourseView, { user: adult })
    await typeTopic(m, 'Inglés')

    await byId(m.wrapper, 'material-no').trigger('click')
    await byId(m.wrapper, 'material-next').trigger('click')
    await byId(m.wrapper, 'material-next').trigger('click')

    expect(byId(m.wrapper, 'thinking').exists()).toBe(true)
    expect(api.startIntake).toHaveBeenCalledTimes(1)
    finish({ intakeId: 'i1', questions: [] })
    await flushPromises()
    expect(byId(m.wrapper, 'thinking').exists()).toBe(false)
  })

  // ---------- paso 3: preguntas ----------

  it('shows each question with its options, the default one already selected and "Como tú veas" last', async () => {
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m)

    expect(byId(m.wrapper, 'question-q1').text()).toContain('¿En qué quieres centrarte?')
    expect((byId(m.wrapper, 'option-q1-o1').element as HTMLInputElement).checked).toBe(true)
    expect((byId(m.wrapper, 'option-q1-o2').element as HTMLInputElement).checked).toBe(false)
    expect(byId(m.wrapper, 'question-q1').text()).toContain('Como tú veas')
    expect((byId(m.wrapper, 'option-q2-o2').element as HTMLInputElement).checked).toBe(true)
  })

  it('goes straight to the summary when the assistant has no questions', async () => {
    vi.mocked(api.startIntake).mockResolvedValue({ intakeId: 'i1', questions: [] } as never)
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m)

    expect(byId(m.wrapper, 'summary').exists()).toBe(true)
    expect(byId(m.wrapper, 'question-q1').exists()).toBe(false)
  })

  // ---------- resumen y aceptar ----------

  it('shows a plain-language summary with the topic and the chosen options and the "generated by AI" notice', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)

    await byId(m.wrapper, 'option-q1-o2').setValue(true)
    await byId(m.wrapper, 'questions-next').trigger('click')

    const summary = byId(m.wrapper, 'summary').text()
    expect(summary).toContain('Inglés de 2º de la ESO')
    expect(summary).toContain('Conversación')
    expect(summary.toLowerCase()).toContain('generado por ia')
  })

  it('accepting creates the course with the chosen answers and goes to its page', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    await byId(m.wrapper, 'option-q1-o2').setValue(true)
    await byId(m.wrapper, 'questions-next').trigger('click')

    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()

    expect(api.createCourse).toHaveBeenCalledWith('i1', [
      { questionId: 'q1', optionId: 'o2' },
      { questionId: 'q2', optionId: 'o2' },
    ])
    expect(m.router.currentRoute.value.fullPath).toBe('/cursos/c1')
  })

  it('a double click on Accept creates only one course', async () => {
    let finish: (v: unknown) => void = () => undefined
    vi.mocked(api.createCourse).mockReturnValue(new Promise((resolve) => (finish = resolve)) as never)
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    await byId(m.wrapper, 'questions-next').trigger('click')

    await byId(m.wrapper, 'accept').trigger('click')
    await byId(m.wrapper, 'accept').trigger('click')
    finish({ id: 'c1' })
    await flushPromises()

    expect(api.createCourse).toHaveBeenCalledTimes(1)
  })

  it('Back keeps the topic and the answers', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    await byId(m.wrapper, 'option-q1-o2').setValue(true)
    await byId(m.wrapper, 'questions-next').trigger('click')

    await byId(m.wrapper, 'back').trigger('click')

    expect((byId(m.wrapper, 'option-q1-o2').element as HTMLInputElement).checked).toBe(true)
    await byId(m.wrapper, 'back').trigger('click')
    await byId(m.wrapper, 'back').trigger('click')
    expect((byId(m.wrapper, 'topic-input').element as HTMLInputElement).value).toBe('Inglés de 2º de la ESO')
  })

  // ---------- errores ----------

  it('shows the agreed message when the topic is blocked, without any reason or code', async () => {
    vi.mocked(api.startIntake).mockRejectedValue(new ApiError(422, 'TOPIC_NOT_ALLOWED'))
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m, 'un tema bloqueado')

    const message = byId(m.wrapper, 'blocked-message').text()
    expect(message).toContain('Contenido inapropiado. Consulta con tu adulto.')
    expect(m.wrapper.text()).not.toContain('TOPIC_NOT_ALLOWED')
    expect(byId(m.wrapper, 'question-q1').exists()).toBe(false)
  })

  it('lets the user go back and write another topic after a block', async () => {
    vi.mocked(api.startIntake).mockRejectedValueOnce(new ApiError(422, 'TOPIC_NOT_ALLOWED')).mockResolvedValue({ intakeId: 'i2', questions: [] } as never)
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m, 'un tema bloqueado')

    await byId(m.wrapper, 'blocked-back').trigger('click')
    await typeTopic(m, 'Matemáticas')
    await byId(m.wrapper, 'material-no').trigger('click')
    await byId(m.wrapper, 'material-next').trigger('click')
    await flushPromises()

    expect(api.startIntake).toHaveBeenLastCalledWith('Matemáticas', [])
    expect(byId(m.wrapper, 'summary').exists()).toBe(true)
  })

  it.each([
    ['RATE_LIMITED', 429, 'muchas'],
    ['COURSE_LIMIT_REACHED', 409, 'pendientes'],
    ['AI_USER_LIMIT', 429, 'límite'],
    ['AI_BUDGET_BLOCKED', 429, 'límite'],
  ])('shows a clear message for %s', async (code, status, fragment) => {
    vi.mocked(api.startIntake).mockRejectedValue(new ApiError(status, code))
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m)

    expect(byId(m.wrapper, 'intake-error').text().toLowerCase()).toContain(fragment)
  })

  it('on an unexpected failure it offers to retry without losing the topic', async () => {
    vi.mocked(api.startIntake).mockRejectedValueOnce(new ApiError(503, 'AI_PROVIDER_UNAVAILABLE'))
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    expect(byId(m.wrapper, 'intake-error').exists()).toBe(true)
    expect(m.wrapper.text()).not.toContain('AI_PROVIDER_UNAVAILABLE')

    await byId(m.wrapper, 'retry').trigger('click')
    await flushPromises()

    expect(api.startIntake).toHaveBeenCalledTimes(2)
    expect(api.startIntake).toHaveBeenLastCalledWith('Inglés de 2º de la ESO', [])
    expect(byId(m.wrapper, 'question-q1').exists()).toBe(true)
  })

  it('explains that the query expired and lets the user start again', async () => {
    vi.mocked(api.createCourse).mockRejectedValue(new ApiError(410, 'INTAKE_EXPIRED'))
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    await byId(m.wrapper, 'questions-next').trigger('click')

    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'create-error').text().toLowerCase()).toContain('caducado')
  })

  it('shows a clear message when the course limit is reached while accepting', async () => {
    vi.mocked(api.createCourse).mockRejectedValue(new ApiError(409, 'COURSE_LIMIT_REACHED'))
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)
    await byId(m.wrapper, 'questions-next').trigger('click')

    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'create-error').text().toLowerCase()).toContain('pendientes')
  })

  // ---------- seguridad de la interfaz ----------

  it('renders the topic and the texts that come from the model as plain text, never as HTML', async () => {
    const hostile = { ...q1, text: '<img src=x onerror=alert(1)>', options: [{ id: 'o1', label: '<b>negrita</b>', isDefault: true }, ...q1.options.slice(1)] }
    vi.mocked(api.startIntake).mockResolvedValue({ intakeId: 'i1', questions: [hostile] } as never)
    const m = await mountApp(NewCourseView, { user: adult })

    await goToQuestions(m, '<script>alert(1)</script> física')
    expect(m.wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    await byId(m.wrapper, 'questions-next').trigger('click')

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('b').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(byId(m.wrapper, 'summary').text()).toContain('<script>alert(1)</script> física')
  })

  it('uses no technical jargon in its texts', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await goToQuestions(m)

    const text = m.wrapper.text().toLowerCase()
    for (const word of ['token', 'api', 'json', 'embedding', 'prompt', 'modelo']) expect(text).not.toContain(word)
  })
})
