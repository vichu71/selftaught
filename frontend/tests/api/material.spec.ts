import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return status === 204
    ? new Response(null, { status })
    : new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api client: material', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=token-csrf-123'
  })
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.unstubAllGlobals()
  })

  it('lists the material with a plain GET and no CSRF header', async () => {
    fetchMock.mockResolvedValue(respond(200, { own: [], minors: [] }))

    const result = await api.listMaterial()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/material')
    expect(init.method ?? 'GET').toBe('GET')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
    expect(result).toEqual({ own: [], minors: [] })
  })

  // AC-1 / AC-15: multipart con el token CSRF y sin fijar a mano el Content-Type (lo pone el navegador con el boundary)
  it('uploads the file as multipart form data with the CSRF header and no explicit content type', async () => {
    fetchMock.mockResolvedValue(respond(202, { id: 'd1' }))
    const file = new File(['hola'], 'apuntes.txt', { type: 'text/plain' })

    await api.uploadMaterial(file)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/material')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.body as FormData).get('file')).toBeInstanceOf(File)
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.headers['Content-Type']).toBeUndefined()
    expect(init.credentials).toBe('same-origin')
  })

  it('never puts the file name or any content in the URL', async () => {
    fetchMock.mockResolvedValue(respond(202, { id: 'd1' }))

    await api.uploadMaterial(new File(['x'], 'secreto.txt'))

    expect(String(fetchMock.mock.calls[0][0])).not.toContain('secreto')
  })

  it('deletes by id with the CSRF header, encoding the id', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.deleteMaterial('a/b c')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/material/a%2Fb%20c')
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it('exposes the status and error code of a failed upload', async () => {
    fetchMock.mockResolvedValue(respond(413, { code: 'MATERIAL_TOO_LARGE', message: 'x' }))

    await expect(api.uploadMaterial(new File(['x'], 'a.txt'))).rejects.toMatchObject({
      status: 413,
      code: 'MATERIAL_TOO_LARGE',
    })
    await expect(api.uploadMaterial(new File(['x'], 'a.txt'))).rejects.toBeInstanceOf(ApiError)
  })
})
