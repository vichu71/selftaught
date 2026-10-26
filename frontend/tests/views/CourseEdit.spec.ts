import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      getCourse: vi.fn(), renameTopic: vi.fn(), reorderTopics: vi.fn(), removeTopic: vi.fn(), addTopic: vi.fn(), renameChapter: vi.fn(), reorderChapters: vi.fn(),
      removeChapter: vi.fn(), addChapter: vi.fn(), editLesson: vi.fn(), regeneratePiece: vi.fn(), getPiece: vi.fn(), listShareCodes: vi.fn(), createShareCode: vi.fn(),
      revokeShareCode: vi.fn(), me: vi.fn(),
    },
  }
})
import { ApiError, api } from '@/api/client'
import CourseEditView from '@/views/CourseEditView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const piece = (id: string, outdated = false) => ({ id, status: 'DONE', outdated })

const detail = (over: object = {}) => ({
  id: 'c1', title: 'Historia', status: 'READY', version: 4, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [],
  outline: [
    { id: 't1', title: 'La Antigüedad', summary: 'Egipto y Grecia', chapters: [
      { id: 'ch1', title: 'Egipto', objective: 'Conocer el Nilo', lesson: piece('l1'), exercises: piece('e1', true) },
      { id: 'ch2', title: 'Grecia', objective: 'Entender la polis', lesson: piece('l2'), exercises: piece('e2') },
    ], exam: piece('x1', true) },
    { id: 't2', title: 'La Edad Media', summary: 'Siglos V a XV', chapters: [
      { id: 'ch3', title: 'Feudalismo', objective: 'Señores y vasallos', lesson: piece('l3'), exercises: piece('e3') },
    ], exam: piece('x2') },
  ],
  ...over,
})

const open = (user = adult) => mountApp(CourseEditView, { user, route: '/cursos/c1/editar' })

// El diálogo de confirmación se pinta fuera del componente (Reka lo teletransporta al body)
const inBody = (id: string) => document.body.querySelector<HTMLElement>('[data-testid="' + id + '"]')
const dialogText = () => (inBody('edit-confirm')?.textContent ?? '').toLowerCase()
afterEach(() => { document.body.innerHTML = '' })

async function click(m: Awaited<ReturnType<typeof open>>, id: string) {
  const el = byId(m.wrapper, id)
  if (el.exists()) await el.trigger('click')
  else inBody(id)?.click()
  await flushPromises()
}

async function fill(m: Awaited<ReturnType<typeof open>>, id: string, value: string) {
  await byId(m.wrapper, id).setValue(value)
}

// AC-1 a AC-7, AC-13, AC-16: la pantalla de edición del curso
describe('Edición del curso', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
    vi.mocked(api.listShareCodes).mockResolvedValue([] as never)
  })

  // ---------- mostrar ----------

  it('loads the course of the route and shows its outline and its version', async () => {
    const m = await open()

    expect(api.getCourse).toHaveBeenCalledWith('c1')
    expect(byId(m.wrapper, 'edit-topic-t1').text()).toContain('La Antigüedad')
    expect(byId(m.wrapper, 'edit-chapter-ch2').text()).toContain('Grecia')
    expect(byId(m.wrapper, 'edit-version').text()).toContain('4')
  })

  it('says which pieces are outdated and offers to regenerate them', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'edit-outdated-e1').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-outdated-e1').text().toLowerCase()).toContain('cambi')
    expect(byId(m.wrapper, 'edit-outdated-x1').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-outdated-e2').exists()).toBe(false)
    expect(byId(m.wrapper, 'edit-regenerate-e1').exists()).toBe(true)
  })

  it('shows a not-found message for a course that is not theirs', async () => {
    vi.mocked(api.getCourse).mockRejectedValue(new ApiError(404))
    const m = await open()

    expect(byId(m.wrapper, 'edit-not-found').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-topic-t1').exists()).toBe(false)
  })

  // ---------- estado del curso (AC-6) ----------

  it.each(['PENDING_GENERATION', 'GENERATING'])('blocks every action and says why while the course is %s', async (status) => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'edit-busy').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-topic-down-t1').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-topic-add').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-regenerate-e1').attributes('disabled')).toBeDefined()
  })

  it('lets a paused course be edited', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'PAUSED' }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'edit-busy').exists()).toBe(false)
    expect(byId(m.wrapper, 'edit-topic-down-t1').attributes('disabled')).toBeUndefined()
  })

  // ---------- reordenar ----------

  it('moves a topic down and sends the whole new order, showing what the server answers', async () => {
    vi.mocked(api.reorderTopics).mockResolvedValue(detail({ version: 5, outline: [detail().outline[1], detail().outline[0]] }) as never)
    const m = await open()

    await click(m, 'edit-topic-down-t1')

    expect(api.reorderTopics).toHaveBeenCalledWith('c1', ['t2', 't1'])
    expect(byId(m.wrapper, 'edit-version').text()).toContain('5')
    const html = m.wrapper.html()
    expect(html.indexOf('edit-topic-t2')).toBeLessThan(html.indexOf('edit-topic-t1'))
  })

  it('cannot move the first topic up nor the last one down', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'edit-topic-up-t1').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-topic-down-t2').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-chapter-up-ch1').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-chapter-down-ch2').attributes('disabled')).toBeDefined()
  })

  it('moves a chapter inside its topic', async () => {
    vi.mocked(api.reorderChapters).mockResolvedValue(detail() as never)
    const m = await open()

    await click(m, 'edit-chapter-down-ch1')

    expect(api.reorderChapters).toHaveBeenCalledWith('c1', 't1', ['ch2', 'ch1'])
  })

  // ---------- renombrar ----------

  it('renames a topic with its title and summary', async () => {
    vi.mocked(api.renameTopic).mockResolvedValue(detail({ version: 5 }) as never)
    const m = await open()

    await click(m, 'edit-topic-rename-t1')
    await fill(m, 'edit-title', 'La Edad Antigua')
    await fill(m, 'edit-summary', 'Egipto, Grecia y Roma')
    await click(m, 'edit-save')

    expect(api.renameTopic).toHaveBeenCalledWith('c1', 't1', { title: 'La Edad Antigua', summary: 'Egipto, Grecia y Roma' })
    expect(byId(m.wrapper, 'edit-title').exists()).toBe(false)
  })

  it('renames a chapter with its title and objective', async () => {
    vi.mocked(api.renameChapter).mockResolvedValue(detail() as never)
    const m = await open()

    await click(m, 'edit-chapter-rename-ch2')
    await fill(m, 'edit-title', 'Grecia clásica')
    await fill(m, 'edit-objective', 'Atenas y Esparta')
    await click(m, 'edit-save')

    expect(api.renameChapter).toHaveBeenCalledWith('c1', 'ch2', { title: 'Grecia clásica', objective: 'Atenas y Esparta' })
  })

  it('sends nothing and says so when a name is empty or too long', async () => {
    const m = await open()

    await click(m, 'edit-topic-rename-t1')
    await fill(m, 'edit-title', '   ')
    await click(m, 'edit-save')
    expect(api.renameTopic).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'edit-error').exists()).toBe(true)

    await fill(m, 'edit-title', 'x'.repeat(121))
    await click(m, 'edit-save')
    expect(api.renameTopic).not.toHaveBeenCalled()
  })

  it('lets the user cancel a rename without sending anything', async () => {
    const m = await open()

    await click(m, 'edit-topic-rename-t1')
    await click(m, 'edit-cancel')

    expect(byId(m.wrapper, 'edit-title').exists()).toBe(false)
    expect(api.renameTopic).not.toHaveBeenCalled()
  })

  // ---------- quitar, con confirmación ----------

  it('asks for confirmation before removing a topic and says what is lost', async () => {
    vi.mocked(api.removeTopic).mockResolvedValue(detail({ outline: [detail().outline[1]] }) as never)
    const m = await open()

    await click(m, 'edit-topic-remove-t1')
    expect(api.removeTopic).not.toHaveBeenCalled()
    expect(dialogText()).toContain('progreso')
    await click(m, 'edit-confirm-yes')

    expect(api.removeTopic).toHaveBeenCalledWith('c1', 't1')
    expect(byId(m.wrapper, 'edit-topic-t1').exists()).toBe(false)
  })

  it('lets the user change their mind when removing a chapter', async () => {
    const m = await open()

    await click(m, 'edit-chapter-remove-ch1')
    await click(m, 'edit-confirm-no')

    expect(api.removeChapter).not.toHaveBeenCalled()
    expect(inBody('edit-confirm')).toBeNull()
  })

  it('removes a chapter once confirmed', async () => {
    vi.mocked(api.removeChapter).mockResolvedValue(detail() as never)
    const m = await open()

    await click(m, 'edit-chapter-remove-ch1')
    await click(m, 'edit-confirm-yes')

    expect(api.removeChapter).toHaveBeenCalledWith('c1', 'ch1')
  })

  it('does not offer to remove the last topic nor the last chapter of a topic', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ outline: [{ ...detail().outline[1] }] }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'edit-topic-remove-t2').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-chapter-remove-ch3').attributes('disabled')).toBeDefined()
  })

  // ---------- añadir con IA ----------

  it('adds a chapter to a topic and tells the course is queued for the AI', async () => {
    vi.mocked(api.addChapter).mockResolvedValue(detail({ status: 'PENDING_GENERATION', version: 5 }) as never)
    const m = await open()

    await click(m, 'edit-chapter-add-t1')
    await fill(m, 'edit-title', 'Roma')
    await fill(m, 'edit-objective', 'Entender la república')
    await click(m, 'edit-save')

    expect(api.addChapter).toHaveBeenCalledWith('c1', 't1', { title: 'Roma', objective: 'Entender la república' })
    expect(byId(m.wrapper, 'edit-busy').exists()).toBe(true)
  })

  it('adds a topic together with its first chapter', async () => {
    vi.mocked(api.addTopic).mockResolvedValue(detail({ status: 'PENDING_GENERATION' }) as never)
    const m = await open()

    await click(m, 'edit-topic-add')
    await fill(m, 'edit-title', 'La Edad Moderna')
    await fill(m, 'edit-summary', 'Del Renacimiento a las revoluciones')
    await fill(m, 'edit-chapter-title', 'El Renacimiento')
    await fill(m, 'edit-objective', 'Entender el humanismo')
    await click(m, 'edit-save')

    expect(api.addTopic).toHaveBeenCalledWith('c1', { title: 'La Edad Moderna', summary: 'Del Renacimiento a las revoluciones', chapter: { title: 'El Renacimiento', objective: 'Entender el humanismo' } })
  })

  it('warns that adding uses the AI and counts for the limit', async () => {
    const m = await open()

    await click(m, 'edit-chapter-add-t1')

    expect(byId(m.wrapper, 'edit-ai-notice').text().toLowerCase()).toContain('ia')
  })

  it('does not send a topic without its first chapter', async () => {
    const m = await open()

    await click(m, 'edit-topic-add')
    await fill(m, 'edit-title', 'Tema')
    await fill(m, 'edit-summary', 'Resumen')
    await click(m, 'edit-save')

    expect(api.addTopic).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'edit-error').exists()).toBe(true)
  })

  // ---------- editar una lección (AC-3) ----------

  it('opens the text of a lesson, previews it and saves it', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'l1', kind: 'LESSON', status: 'DONE', title: 'Egipto', generatedByAi: true, markdown: '# Egipto\n\nTexto', sources: [] } as never)
    vi.mocked(api.editLesson).mockResolvedValue(detail({ version: 5 }) as never)
    const m = await open()

    await click(m, 'edit-lesson-l1')
    expect(api.getPiece).toHaveBeenCalledWith('c1', 'l1')
    expect((byId(m.wrapper, 'lesson-text').element as HTMLTextAreaElement).value).toBe('# Egipto\n\nTexto')
    await fill(m, 'lesson-text', '# Egipto\n\nTexto **mejorado**')
    expect(byId(m.wrapper, 'lesson-preview').find('strong').text()).toBe('mejorado')
    await click(m, 'lesson-save')

    expect(api.editLesson).toHaveBeenCalledWith('c1', 'l1', '# Egipto\n\nTexto **mejorado**')
    expect(byId(m.wrapper, 'lesson-text').exists()).toBe(false)
  })

  it('limits the lesson to twenty thousand characters and does not send an empty one', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'l1', kind: 'LESSON', status: 'DONE', title: 'Egipto', generatedByAi: true, markdown: 'Texto', sources: [] } as never)
    const m = await open()

    await click(m, 'edit-lesson-l1')
    expect(byId(m.wrapper, 'lesson-text').attributes('maxlength')).toBe('20000')
    await fill(m, 'lesson-text', '   ')
    await click(m, 'lesson-save')

    expect(api.editLesson).not.toHaveBeenCalled()
  })

  it('renders hostile text in the preview as plain text', async () => {
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'l1', kind: 'LESSON', status: 'DONE', title: 'x', generatedByAi: true, markdown: '<img src=x onerror=alert(1)> [c](javascript:alert(1))', sources: [] } as never)
    const m = await open()

    await click(m, 'edit-lesson-l1')

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('a[href^="javascript"]').exists()).toBe(false)
  })

  // ---------- regenerar (AC-4) ----------

  it('asks for confirmation before regenerating and warns about what is deleted', async () => {
    vi.mocked(api.regeneratePiece).mockResolvedValue(detail({ status: 'PENDING_GENERATION' }) as never)
    const m = await open()

    await click(m, 'edit-regenerate-e1')
    expect(api.regeneratePiece).not.toHaveBeenCalled()
    expect(dialogText()).toContain('intentos')
    await click(m, 'edit-confirm-yes')

    expect(api.regeneratePiece).toHaveBeenCalledWith('c1', 'e1')
    expect(byId(m.wrapper, 'edit-busy').exists()).toBe(true)
  })

  // ---------- errores (AC-16) ----------

  it.each([
    ['COURSE_BUSY', 409, 'generando'],
    ['EDIT_RATE_LIMITED', 429, 'muchas'],
    ['COURSE_TOO_BIG', 422, 'demasiad'],
    ['EDIT_NOT_ALLOWED', 422, 'contenido inapropiado'],
  ])('explains the %s error in plain words without showing the code', async (code, status, fragment) => {
    vi.mocked(api.renameTopic).mockRejectedValue(new ApiError(status, code))
    const m = await open(code === 'EDIT_NOT_ALLOWED' ? minor : adult)

    await click(m, 'edit-topic-rename-t1')
    await fill(m, 'edit-title', 'Nuevo')
    await click(m, 'edit-save')

    expect(byId(m.wrapper, 'edit-error').text().toLowerCase()).toContain(fragment)
    expect(m.wrapper.text()).not.toContain(code)
  })

  it('shows a generic error for anything else and keeps what the user typed', async () => {
    vi.mocked(api.renameTopic).mockRejectedValue(new ApiError(500))
    const m = await open()

    await click(m, 'edit-topic-rename-t1')
    await fill(m, 'edit-title', 'Nuevo título')
    await click(m, 'edit-save')

    expect(byId(m.wrapper, 'edit-error').text().toLowerCase()).toContain('no se pudo')
    expect((byId(m.wrapper, 'edit-title').element as HTMLInputElement).value).toBe('Nuevo título')
  })

  it('shows the course title as plain text even if it is hostile', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ title: '<img src=x onerror=alert(1)>' }) as never)
    const m = await open()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('blocks a second action while one is in progress', async () => {
    let finish: (value: unknown) => void = () => undefined
    vi.mocked(api.reorderTopics).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const m = await open()

    await byId(m.wrapper, 'edit-topic-down-t1').trigger('click')
    await byId(m.wrapper, 'edit-topic-down-t1').trigger('click')
    finish(detail())
    await flushPromises()

    expect(api.reorderTopics).toHaveBeenCalledTimes(1)
  })
})
