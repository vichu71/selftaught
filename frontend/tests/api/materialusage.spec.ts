import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// F18 ART-003: el cliente del uso del material; solo lectura, sin parámetros (el usuario es el de la sesión)
describe('api client: uso del material', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('getMaterialUsage asks the usage address with GET and returns the map by document', async () => {
    fetchMock.mockResolvedValue(respond(200, { usage: { d1: [{ id: 'c1', title: 'Curso' }] } }))

    const result = await api.getMaterialUsage()

    const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
    expect(url).toBe('/api/courses/material-usage')
    expect(init.method).toBe('GET')
    expect(init.body).toBeUndefined()
    expect(result).toEqual({ d1: [{ id: 'c1', title: 'Curso' }] })
  })

  it('returns an empty map when the server says there is no usage', async () => {
    fetchMock.mockResolvedValue(respond(200, { usage: {} }))

    expect(await api.getMaterialUsage()).toEqual({})
  })

  it('turns an error into an ApiError with its status', async () => {
    fetchMock.mockResolvedValue(respond(401))

    const error = await api.getMaterialUsage().catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(401)
  })
})
