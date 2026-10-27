import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { me: vi.fn(), logout: vi.fn(), ownerAi: vi.fn(), ownerPrices: vi.fn() } }
})
import { api } from '@/api/client'
import NavBar from '@/layouts/NavBar.vue'
import { adult, byId, minor, mountApp, ownerUser } from '../helpers'

// F14 AC-12: el enlace del menú y la ruta existen solo para el propietario (el permiso real lo da el servidor)
describe('panel del propietario: menú y ruta', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.ownerAi).mockResolvedValue({} as never)
    vi.mocked(api.ownerPrices).mockResolvedValue({ models: [], images: [] } as never)
  })

  it('shows the «Propietario» link, pointing to /propietario, only to the owner', async () => {
    const { wrapper } = await mountApp(NavBar, { user: ownerUser, route: '/' })

    expect(byId(wrapper, 'nav-owner').attributes('href')).toBe('/propietario')
    expect(byId(wrapper, 'nav-owner').text().toLowerCase()).toContain('propietario')
  })

  it.each([['an adult who is not the owner', adult], ['a minor', minor]])('does not show the link to %s', async (_name, user) => {
    const { wrapper } = await mountApp(NavBar, { user, route: '/' })

    expect(byId(wrapper, 'nav-owner').exists()).toBe(false)
  })

  it('marks the owner section as current and only that one', async () => {
    const { wrapper } = await mountApp(NavBar, { user: ownerUser, route: '/propietario' })

    expect(byId(wrapper, 'nav-owner').attributes('aria-current')).toBe('page')
    expect(byId(wrapper, 'nav-map').attributes('aria-current')).toBeUndefined()
  })

  it('lets the owner into /propietario', async () => {
    const { router } = await mountApp(NavBar, { user: ownerUser, route: '/propietario' })

    expect(router.currentRoute.value.path).toBe('/propietario')
    expect(router.currentRoute.value.name).toBe('owner')
  })

  it.each([['an adult who is not the owner', adult], ['a minor', minor]])('sends %s who types the address back to the start, like any unknown page', async (_name, user) => {
    const { router } = await mountApp(NavBar, { user, route: '/propietario' })

    expect(router.currentRoute.value.path).toBe('/')
    expect(api.ownerAi).not.toHaveBeenCalled()
  })

  it('sends an anonymous visitor to the login', async () => {
    const { router } = await mountApp(NavBar, { user: null, route: '/propietario' })

    expect(router.currentRoute.value.name).toBe('login')
  })
})
