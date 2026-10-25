import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listStudents: vi.fn(), logout: vi.fn(), me: vi.fn(), listCourses: vi.fn() } }
})
import { api } from '@/api/client'
import HomeView from '@/views/HomeView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

// AC-9: pantalla de inicio vacía tras el login
describe('HomeView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([])
  })

  it('greets the adult by name and shows the students panel', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'welcome').text()).toContain('Ana')
    expect(byId(wrapper, 'students-panel').exists()).toBe(true)
    expect(api.listStudents).toHaveBeenCalled()
  })

  it('greets a minor by name, without the students panel and without calling tutor endpoints', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'welcome').text()).toContain('Hugo')
    expect(byId(wrapper, 'students-panel').exists()).toBe(false)
    expect(api.listStudents).not.toHaveBeenCalled()
  })

  // PROJECT.md §8: el menor sabe que su tutor ve su actividad
  it('tells a minor that their tutor can see their activity', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })
    expect(byId(wrapper, 'guardian-notice').exists()).toBe(true)
  })

  // F08 AC-10: el aviso nombra lo que el adulto puede ver, también respuestas e intentos
  it('names answers, attempts and conversations in the notice for a minor', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    const text = byId(wrapper, 'guardian-notice').text().toLowerCase()
    expect(text).toContain('respuestas')
    expect(text).toContain('intentos')
    expect(text).toContain('conversaciones')
  })

  it('does not show that notice to adults', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })
    expect(byId(wrapper, 'guardian-notice').exists()).toBe(false)
  })

  // AC-2: cerrar sesión
  it('logs out and goes back to the login', async () => {
    vi.mocked(api.logout).mockResolvedValue(undefined)
    const { wrapper, router, auth } = await mountApp(HomeView, { user: adult })

    await byId(wrapper, 'logout').trigger('click')
    await flushPromises()

    expect(api.logout).toHaveBeenCalled()
    expect(auth.user).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('declares a responsive viewport-friendly layout container', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })
    expect(byId(wrapper, 'app-shell').exists()).toBe(true)
  })
})
