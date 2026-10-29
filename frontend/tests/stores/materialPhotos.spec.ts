import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { listMaterial: vi.fn(), uploadMaterial: vi.fn(), getMaterialCapabilities: vi.fn(), getMaterialUsage: vi.fn(), deleteMaterial: vi.fn() },
  }
})
import { ApiError, api } from '@/api/client'
import { useMaterialStore } from '@/stores/material'

const photo = (name = 'apuntes.jpg', size = 1000) => new File([new Uint8Array(size)], name, { type: 'image/jpeg' })

// F19 AC-14: la tienda conoce las capacidades y deja subir fotos solo cuando el servidor las ofrece; la comprobación del cliente es de comodidad
describe('stores/material: fotos', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [], minors: [] } as never)
    vi.mocked(api.uploadMaterial).mockResolvedValue({ id: 'd9' } as never)
  })

  it('has no capabilities until they are loaded, and loads them from the server', async () => {
    vi.mocked(api.getMaterialCapabilities).mockResolvedValue({ images: true })
    const store = useMaterialStore()
    expect(store.capabilities).toBeNull()

    await store.loadCapabilities()

    expect(store.capabilities).toEqual({ images: true })
  })

  it('falls back to no photos when the capabilities cannot be read, without breaking the list', async () => {
    vi.mocked(api.getMaterialCapabilities).mockRejectedValue(new ApiError(500))
    const store = useMaterialStore()

    await store.loadCapabilities()
    await store.load()

    expect(store.capabilities).toBeNull()
    expect(store.listError).toBe(false)
  })

  it('refuses a photo while the server does not offer them, with no request', async () => {
    const store = useMaterialStore()

    const id = await store.upload(photo())

    expect(id).toBe('')
    expect(api.uploadMaterial).not.toHaveBeenCalled()
    expect(store.uploadError).toContain('PDF')
  })

  it.each(['apuntes.jpg', 'apuntes.JPEG', 'pizarra.png'])('uploads %s when photos are on and returns the id', async (name) => {
    vi.mocked(api.getMaterialCapabilities).mockResolvedValue({ images: true })
    const store = useMaterialStore()
    await store.loadCapabilities()

    const id = await store.upload(photo(name))

    expect(id).toBe('d9')
    expect(store.uploadError).toBe('')
  })

  it.each(['foto.webp', 'foto.heic', 'foto.gif', 'foto.svg'])('still refuses %s even with photos on, and says what is accepted', async (name) => {
    vi.mocked(api.getMaterialCapabilities).mockResolvedValue({ images: true })
    const store = useMaterialStore()
    await store.loadCapabilities()

    const id = await store.upload(photo(name))

    expect(id).toBe('')
    expect(api.uploadMaterial).not.toHaveBeenCalled()
    expect(store.uploadError).toContain('JPEG')
    expect(store.uploadError).toContain('PNG')
  })

  it.each([
    ['CHILD_IMAGES_NOT_ENABLED', 'adulto'],
    ['IMAGE_READ_RATE_LIMIT', 'hoy'],
    ['MATERIAL_TOO_LARGE', 'MB'],
    ['IMAGE_INVALID', 'foto'],
    ['IMAGE_TOO_LARGE', 'grande'],
  ])('turns the server code %s into a sentence and never shows the code', async (code, word) => {
    vi.mocked(api.getMaterialCapabilities).mockResolvedValue({ images: true })
    vi.mocked(api.uploadMaterial).mockRejectedValue(new ApiError(403, code))
    const store = useMaterialStore()
    await store.loadCapabilities()

    await store.upload(photo())

    expect(store.uploadError).toContain(word)
    expect(store.uploadError).not.toContain(code)
  })
})
