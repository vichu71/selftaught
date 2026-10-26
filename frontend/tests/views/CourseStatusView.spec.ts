import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getCourse: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CourseStatusView from '@/views/CourseStatusView.vue'
import { adult, byId, mountApp } from '../helpers'

const course = {
  id: 'c1', title: 'Inglés de 2º de la ESO', status: 'PENDING_GENERATION', createdAt: '2026-10-25T10:00:00Z',
  sheet: {
    schemaVersion: 1, topic: 'Inglés de 2º de la ESO', focus: 'Gramática y vocabulario', startLevel: 'Sé algo',
    goal: 'Aprobar el curso', topicsCount: 6, examPerTopic: true, documentIds: ['d1'],
  },
  documents: [{ id: 'd1', name: 'apuntes.pdf' }],
}

// AC-7: pantalla del curso tras aceptar
describe('CourseStatusView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(course as never)
  })

  it('loads the course of the route and shows its title', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(api.getCourse).toHaveBeenCalledWith('c1')
    expect(byId(m.wrapper, 'course-title').text()).toContain('Inglés de 2º de la ESO')
  })

  it('says in plain words that the course is waiting to be prepared', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-status').text().toLowerCase()).toContain('en cola')
  })

  it('shows what was agreed: focus, topics, exam and documents', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    const text = byId(m.wrapper, 'course-sheet').text()
    expect(text).toContain('Gramática y vocabulario')
    expect(text).toContain('6')
    expect(text).toContain('apuntes.pdf')
  })

  it('carries the generated-by-AI notice', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(m.wrapper.text().toLowerCase()).toContain('generado por ia')
  })

  it('shows a clear message when the course does not exist or is not yours', async () => {
    vi.mocked(api.getCourse).mockRejectedValue(new ApiError(404, 'NOT_FOUND'))
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/otro' })

    expect(byId(m.wrapper, 'course-not-found').exists()).toBe(true)
    expect(byId(m.wrapper, 'course-title').exists()).toBe(false)
  })

  it('shows a generic error for any other failure', async () => {
    vi.mocked(api.getCourse).mockRejectedValue(new ApiError(500))
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-error').exists()).toBe(true)
  })

  it('renders a hostile title as plain text', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ ...course, title: '<img src=x onerror=alert(1)>' } as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-title').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  it('offers a way back home', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })
    await flushPromises()

    expect(byId(m.wrapper, 'back-home').attributes('href')).toBe('/')
  })
})

// F09 AC-7, AC-10, AC-16: edición, versión y origen de un curso
describe('CourseStatusView: edición y copia (F09)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue({ ...course, status: 'READY', version: 3 } as never)
  })

  it('offers to edit the course and shows its version', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'edit-link').attributes('href')).toBe('/cursos/c1/editar')
    expect(byId(m.wrapper, 'course-version').text()).toContain('3')
  })

  it('says when the course is a copy and of which version, without any id', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ ...course, status: 'READY', version: 1, copy: { originVersion: 4 } } as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    const text = byId(m.wrapper, 'copy-notice').text()
    expect(text.toLowerCase()).toContain('copia')
    expect(text).toContain('4')
  })

  it('does not say it is a copy when it is not', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'copy-notice').exists()).toBe(false)
  })
})
