import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      listStudents: vi.fn(), logout: vi.fn(), me: vi.fn(), listCourses: vi.fn(), getStudyProgress: vi.fn(), getCourse: vi.fn(), getStudy: vi.fn(),
      getPiece: vi.fn(), removeTopic: vi.fn(), listShareCodes: vi.fn(), tutorThread: vi.fn(), getTutorThread: vi.fn(), getTutor: vi.fn(),
    },
  }
})
import { ApiError, api } from '@/api/client'
import CourseEditView from '@/views/CourseEditView.vue'
import CourseStatusView from '@/views/CourseStatusView.vue'
import HomeView from '@/views/HomeView.vue'
import { adult, byId, minor, mountApp } from '../helpers'
import { outline, study } from '../map/fixtures'

const course = (id: string, title: string, status = 'READY') => ({
  id, title, status, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [],
})
const detail = (id = 'c1', over: object = {}) => ({ ...course(id, 'Historia antigua'), version: 4, outline: outline(), ...over })

// F10 AC-7: Inicio
describe('Inicio rediseñado', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([course('c1', 'Historia antigua'), course('c2', 'Fracciones')] as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue([{ courseId: 'c1', percent: 38 }, { courseId: 'c2', percent: 8 }])
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
    vi.mocked(api.getStudy).mockResolvedValue(study())
  })

  it('offers to keep reading where the user left off, with a link to that lesson', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    const card = byId(wrapper, 'continue-card')
    expect(card.exists()).toBe(true)
    expect(card.text()).toContain('El Nilo y su gente')
    expect(card.text()).toContain('Historia antigua')
    expect(byId(wrapper, 'continue-link').attributes('href')).toBe('/cursos/c1/piezas/l3')
    expect(api.getCourse).toHaveBeenCalledWith('c1')
    expect(api.getStudy).toHaveBeenCalledWith('c1')
  })

  it('skips courses that are not ready or are already finished', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([
      course('c0', 'En cola', 'GENERATING'), course('c9', 'Terminado'), course('c2', 'Fracciones'),
    ] as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue([{ courseId: 'c9', percent: 100 }, { courseId: 'c2', percent: 8 }])
    vi.mocked(api.getCourse).mockResolvedValue(detail('c2', { title: 'Fracciones' }) as never)

    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(api.getCourse).toHaveBeenCalledTimes(1)
    expect(api.getCourse).toHaveBeenCalledWith('c2')
    expect(byId(wrapper, 'continue-link').attributes('href')).toBe('/cursos/c2/piezas/l3')
  })

  it('invites to create the first course when there are none, with no continue card', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([])

    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'continue-card').exists()).toBe(false)
    expect(byId(wrapper, 'no-courses').exists()).toBe(true)
    expect(byId(wrapper, 'new-course-link').attributes('href')).toBe('/nuevo-curso')
  })

  it('still works when the study data cannot be loaded', async () => {
    vi.mocked(api.getStudy).mockRejectedValue(new ApiError(500, 'X'))

    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'continue-card').exists()).toBe(false)
    expect(byId(wrapper, 'course-link-c1').exists()).toBe(true)
  })

  it('shows the progress of each course as a progress bar with text', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    const bar = byId(wrapper, 'course-c1').find('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('38')
    expect(byId(wrapper, 'course-percent-c1').text()).toContain('38')
  })

  it('keeps the greeting, the tutor panel for adults and the notice for minors', async () => {
    const a = await mountApp(HomeView, { user: adult })
    expect(byId(a.wrapper, 'welcome').text()).toContain('Ana')
    expect(byId(a.wrapper, 'students-panel').exists()).toBe(true)

    const m = await mountApp(HomeView, { user: minor })
    expect(byId(m.wrapper, 'guardian-notice').text().toLowerCase()).toMatch(/respuestas.*intentos.*conversaciones|intentos.*respuestas.*conversaciones/)
    expect(byId(m.wrapper, 'students-panel').exists()).toBe(false)
  })
})

// F10 AC-4, AC-8: el estado del curso es el mapa
describe('Estado del curso con mapa', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
    vi.mocked(api.getStudy).mockResolvedValue(study())
  })

  it('draws the course as a map with the same stops in a list', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-map').exists()).toBe(true)
    expect(byId(m.wrapper, 'map-item-l3').attributes('aria-current')).toBe('step')
    expect(byId(m.wrapper, 'map-link-l3').attributes('href')).toBe('/cursos/c1/piezas/l3')
  })

  it('has no map while the course has no outline yet, and says it is waiting', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail('c1', { status: 'PENDING_GENERATION', outline: undefined }) as never)

    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-map').exists()).toBe(false)
    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).toContain('en cola')
  })

  it('keeps the title, version, copy notice, AI notice and the way to edit', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail('c1', { copy: { originVersion: 2 } }) as never)

    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-title').text()).toContain('Historia antigua')
    expect(byId(m.wrapper, 'course-version').text()).toContain('4')
    expect(byId(m.wrapper, 'copy-notice').text()).toContain('2')
    expect(byId(m.wrapper, 'edit-link').attributes('href')).toBe('/cursos/c1/editar')
    expect(m.wrapper.text().toLowerCase()).toContain('generado por ia')
  })

  it('shows a hostile topic title as text', async () => {
    const o = outline()
    o[0].title = '<img src=x onerror=alert(1)>'
    vi.mocked(api.getCourse).mockResolvedValue(detail('c1', { outline: o }) as never)

    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(m.wrapper.find('img').exists()).toBe(false)
  })
})

// F10 AC-2, AC-8: edición en pestañas y confirmaciones con diálogo
describe('Edición con pestañas y diálogo', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
    vi.mocked(api.listShareCodes).mockResolvedValue([] as never)
  })

  it('splits the screen in two tabs, "Temario" and "Compartir", showing one panel at a time', async () => {
    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    expect(byId(m.wrapper, 'edit-tab-outline').attributes('role')).toBe('tab')
    expect(byId(m.wrapper, 'edit-tab-outline').attributes('aria-selected')).toBe('true')
    expect(byId(m.wrapper, 'edit-topic-t1').exists()).toBe(true)
    expect(byId(m.wrapper, 'share-section').exists()).toBe(false)

    await byId(m.wrapper, 'edit-tab-share').trigger('mousedown', { button: 0 })
    await flushPromises()

    expect(byId(m.wrapper, 'share-section').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-topic-t1').exists()).toBe(false)
  })

  it('asks for confirmation in a modal dialog, never with the browser confirm', async () => {
    const browserConfirm = vi.spyOn(window, 'confirm')
    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    await byId(m.wrapper, 'edit-topic-remove-t1').trigger('click')
    await flushPromises()

    expect(browserConfirm).not.toHaveBeenCalled()
    expect(document.body.querySelector('[role="dialog"] [data-testid="edit-confirm-yes"]')).not.toBeNull()
    expect(api.removeTopic).not.toHaveBeenCalled()
  })

  it('keeps the version and the busy notice', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail('c1', { status: 'GENERATING' }) as never)

    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    expect(byId(m.wrapper, 'edit-version').text()).toContain('4')
    expect(byId(m.wrapper, 'edit-busy').exists()).toBe(true)
  })
})

// F10 AC-9, AC-8: la lectura de la lección
describe('Lectura de la lección', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getTutorThread).mockResolvedValue({ messages: [], remaining: 30 } as never)
  })

  const lesson = (markdown: string) => ({
    id: 'l3', kind: 'LESSON', status: 'DONE', title: 'El Nilo y su gente', content: { markdown }, sources: [],
  })

  it('shows the lesson inside a reading container with the AI notice, and keeps the Markdown safe', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(lesson('# Hola\n\n<img src=x onerror=alert(1)>\n\n[malo](javascript:alert(1))') as never)
    const { default: PieceView } = await import('@/views/PieceView.vue')

    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/l3' })

    expect(byId(m.wrapper, 'lesson-reader').exists()).toBe(true)
    expect(byId(m.wrapper, 'lesson-reader').find('.markdown').exists() || byId(m.wrapper, 'lesson').exists()).toBe(true)
    expect(m.wrapper.text().toLowerCase()).toContain('generado por ia')
    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.findAll('a').some((a) => (a.attributes('href') ?? '').startsWith('javascript'))).toBe(false)
  })
})
