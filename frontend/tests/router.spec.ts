import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import { createAppRouter } from '@/router'
import { adult } from './helpers'

function newRouter() {
  setActivePinia(createPinia())
  return createAppRouter(createMemoryHistory())
}

// AC-10: las rutas protegidas exigen sesión
describe('router guards', () => {
  beforeEach(() => vi.resetAllMocks())

  it('redirects anonymous visitors from the home to the login', async () => {
    vi.mocked(api.me).mockRejectedValue(new ApiError(401))
    const router = newRouter()

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('lets a logged-in user reach the home', async () => {
    vi.mocked(api.me).mockResolvedValue(adult)
    const router = newRouter()

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('sends a logged-in user away from the login page', async () => {
    vi.mocked(api.me).mockResolvedValue(adult)
    const router = newRouter()

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it.each(['/login', '/register', '/verify-email', '/forgot-password', '/reset-password'])(
    'keeps %s public for anonymous visitors',
    async (path) => {
      vi.mocked(api.me).mockRejectedValue(new ApiError(401))
      const router = newRouter()

      await router.push(path)

      expect(router.currentRoute.value.path).toBe(path)
    },
  )

  it('sends unknown routes to the login when anonymous', async () => {
    vi.mocked(api.me).mockRejectedValue(new ApiError(401))
    const router = newRouter()

    await router.push('/lo-que-sea')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('asks the server for the session only once', async () => {
    vi.mocked(api.me).mockResolvedValue(adult)
    const router = newRouter()

    await router.push('/')
    await router.push('/forgot-password')
    await router.push('/')

    expect(api.me).toHaveBeenCalledTimes(1)
  })
})
