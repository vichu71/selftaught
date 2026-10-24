import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { login: vi.fn(), logout: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { adult, minor } from '../helpers'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  // AC-2
  it('login stores the user returned by the API', async () => {
    vi.mocked(api.login).mockResolvedValue(adult)
    const auth = useAuthStore()

    await auth.login('ana@example.com', 'Correct-Horse-9')

    expect(auth.user).toEqual(adult)
    expect(api.login).toHaveBeenCalledWith('ana@example.com', 'Correct-Horse-9')
  })

  it('a failed login leaves the store anonymous and rethrows the error', async () => {
    vi.mocked(api.login).mockRejectedValue(new ApiError(401))
    const auth = useAuthStore()

    await expect(auth.login('ana@example.com', 'mal')).rejects.toMatchObject({ status: 401 })
    expect(auth.user).toBeNull()
  })

  // AC-8
  it('exposes the roles of the current user', async () => {
    const auth = useAuthStore()
    auth.user = adult
    expect(auth.isTutor).toBe(true)
    auth.user = minor
    expect(auth.isTutor).toBe(false)
  })

  // AC-2: cerrar sesión
  it('logout clears the user even when the API call fails', async () => {
    vi.mocked(api.logout).mockRejectedValue(new ApiError(500))
    const auth = useAuthStore()
    auth.user = adult

    await auth.logout().catch(() => undefined)

    expect(auth.user).toBeNull()
  })

  it('loadMe fills the user and marks the session as checked', async () => {
    vi.mocked(api.me).mockResolvedValue(minor)
    const auth = useAuthStore()

    await auth.loadMe()

    expect(auth.user).toEqual(minor)
    expect(auth.ready).toBe(true)
  })

  it('loadMe on a 401 leaves the user anonymous but still marks the session as checked', async () => {
    vi.mocked(api.me).mockRejectedValue(new ApiError(401))
    const auth = useAuthStore()
    auth.user = adult

    await auth.loadMe()

    expect(auth.user).toBeNull()
    expect(auth.ready).toBe(true)
  })
})
