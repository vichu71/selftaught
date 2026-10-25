import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listStudents: vi.fn(), logout: vi.fn(), me: vi.fn(), listMaterial: vi.fn(), listCourses: vi.fn() } }
})
import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import HomeView from '@/views/HomeView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

// AC-16: se llega a «Mi material» desde el inicio y la ruta está protegida
describe('Material navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([])
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [], minors: [] } as never)
  })

  it('shows a link to My material on the home page for an adult', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'material-link').attributes('href')).toBe('/material')
  })

  it('shows the same link to a minor', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'material-link').attributes('href')).toBe('/material')
  })

  it('redirects an anonymous visitor from /material to the login', async () => {
    setActivePinia(createPinia())
    vi.mocked(api.me).mockRejectedValue(new Error('401'))
    const router = createAppRouter(createMemoryHistory())

    await router.push('/material')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets a signed-in user reach /material', async () => {
    const { router } = await mountApp(HomeView, { user: adult, route: '/material' })

    expect(router.currentRoute.value.name).toBe('material')
  })
})
