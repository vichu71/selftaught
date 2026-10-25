import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listCourses: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CoursesPanel from '@/components/CoursesPanel.vue'
import { adult, byId, mountApp } from '../helpers'

const course = (id: string, title: string, status = 'PENDING_GENERATION') => ({
  id, title, status, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [],
})

// AC-16: «Tus cursos» en el inicio
describe('CoursesPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCourses).mockResolvedValue([course('c1', 'Inglés'), course('c2', 'Cocina básica', 'READY')] as never)
  })

  it('lists the courses with their title and a readable state', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-c1').text()).toContain('Inglés')
    expect(byId(m.wrapper, 'course-c1').text().toLowerCase()).toContain('en cola')
    expect(byId(m.wrapper, 'course-c2').text().toLowerCase()).toContain('listo')
  })

  it('links each course to its own page', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-link-c1').attributes('href')).toBe('/cursos/c1')
  })

  it('always offers the way to create a new course', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'new-course-link').attributes('href')).toBe('/nuevo-curso')
  })

  it('shows an inviting empty state when there are no courses', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([])
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'no-courses').exists()).toBe(true)
    expect(byId(m.wrapper, 'new-course-link').exists()).toBe(true)
  })

  it('shows an error if the list cannot be loaded', async () => {
    vi.mocked(api.listCourses).mockRejectedValue(new ApiError(500))
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'courses-error').exists()).toBe(true)
  })

  it('renders hostile titles as plain text', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([course('c1', '<img src=x onerror=alert(1)>')] as never)
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-c1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(m.wrapper.find('img').exists()).toBe(false)
  })
})
