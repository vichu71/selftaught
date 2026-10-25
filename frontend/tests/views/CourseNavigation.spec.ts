import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listStudents: vi.fn(), logout: vi.fn(), me: vi.fn(), listCourses: vi.fn(), getCourse: vi.fn(), listMaterial: vi.fn() } }
})
import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import HomeView from '@/views/HomeView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

// AC-1 y AC-16: se llega al asistente desde el inicio y las rutas están protegidas
describe('Course navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([])
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [], minors: [] } as never)
  })

  it('shows "Tus cursos" and the way to a new course on the home page for an adult', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'courses-panel').exists()).toBe(true)
    expect(byId(wrapper, 'new-course-link').attributes('href')).toBe('/nuevo-curso')
  })

  it('shows the same panel to a minor', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'courses-panel').exists()).toBe(true)
    expect(api.listCourses).toHaveBeenCalled()
  })

  it.each(['/nuevo-curso', '/cursos/c1'])('redirects an anonymous visitor from %s to the login', async (path) => {
    setActivePinia(createPinia())
    vi.mocked(api.me).mockRejectedValue(new Error('401'))
    const router = createAppRouter(createMemoryHistory())

    await router.push(path)
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets a signed-in user reach the wizard and a course page', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ id: 'c1', title: 'x', status: 'PENDING_GENERATION', sheet: {}, documents: [], createdAt: '' } as never)
    const wizard = await mountApp(HomeView, { user: adult, route: '/nuevo-curso' })
    expect(wizard.router.currentRoute.value.name).toBe('new-course')

    const course = await mountApp(HomeView, { user: adult, route: '/cursos/c1' })
    expect(course.router.currentRoute.value.name).toBe('course')
    expect(course.router.currentRoute.value.params.id).toBe('c1')
  })
})
