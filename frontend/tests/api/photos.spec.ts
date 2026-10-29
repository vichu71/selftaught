import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// F19 ART-003: capacidades del material y permiso de fotos de un menor (solo su tutor lo cambia)
describe('api client: fotos de apuntes', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('getMaterialCapabilities asks the capabilities address with GET', async () => {
    fetchMock.mockResolvedValue(respond(200, { images: true }))

    const result = await api.getMaterialCapabilities()

    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
    expect(url).toBe('/api/material/capabilities')
    expect(init.method).toBe('GET')
    expect(result).toEqual({ images: true })
  })

  it('getPhotoPermission reads the permission of the student of the path, with the id encoded', async () => {
    fetchMock.mockResolvedValue(respond(200, { enabled: false }))

    const result = await api.getPhotoPermission('s 1/../x')

    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
    expect(url).toBe('/api/tutor/students/s%201%2F..%2Fx/photo-material')
    expect(init.method).toBe('GET')
    expect(result).toEqual({ enabled: false })
  })

  it('setPhotoPermission sends PUT with the new value as JSON', async () => {
    fetchMock.mockResolvedValue(respond(200, { enabled: true }))

    const result = await api.setPhotoPermission('s1', true)

    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
    expect(url).toBe('/api/tutor/students/s1/photo-material')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ enabled: true })
    expect(result).toEqual({ enabled: true })
  })

  it('turns an error into an ApiError with its status (404 when the student is not yours)', async () => {
    fetchMock.mockResolvedValue(respond(404))

    const error = await api.getPhotoPermission('s1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(404)
  })
})
