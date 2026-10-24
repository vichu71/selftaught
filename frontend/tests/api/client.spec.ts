import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  const init = { status, headers: { 'Content-Type': 'application/json' } }
  return status === 204 ? new Response(null, { status }) : new Response(JSON.stringify(body ?? {}), init)
}

describe('api client', () => {
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

  // AC-2
  it('login posts credentials as JSON in the body, never in the URL', async () => {
    fetchMock.mockResolvedValue(respond(200, { id: 'u1' }))
    await api.login('ana@example.com', 'Correct-Horse-9')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ identifier: 'ana@example.com', password: 'Correct-Horse-9' })
    expect(String(url)).not.toContain('Correct-Horse-9')
  })

  // AC-10: la cookie de sesión viaja sola; el token CSRF se envía en cabecera
  it('sends the session cookie and the CSRF header on mutating requests', async () => {
    fetchMock.mockResolvedValue(respond(204))
    await api.logout()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.credentials).toBe('same-origin')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it('does not need a CSRF header for reads', async () => {
    fetchMock.mockResolvedValue(respond(200, []))
    await api.listStudents()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/tutor/students')
    expect(init.method ?? 'GET').toBe('GET')
  })

  it('maps non-2xx answers to an ApiError carrying the status', async () => {
    fetchMock.mockResolvedValue(respond(429, { code: 'LOCKED' }))

    await expect(api.login('a@b.es', 'x')).rejects.toMatchObject({ status: 429 })
    await expect(api.login('a@b.es', 'x')).rejects.toBeInstanceOf(ApiError)
  })

  it('treats a 204 as success without trying to parse a body', async () => {
    fetchMock.mockResolvedValue(respond(204))
    await expect(api.verifyEmail('abc')).resolves.not.toThrow()
  })

  // AC-4, AC-7: rutas de gestión de menores
  it('uses the tutor endpoints for managing students', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(respond(204)))
    await api.deactivateStudent('s1')
    await api.reactivateStudent('s1')
    await api.resetStudentPassword('s1', 'Nueva-Clave-77x')

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      '/api/tutor/students/s1/deactivate',
      '/api/tutor/students/s1/reactivate',
      '/api/tutor/students/s1/reset-password',
    ])
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ newPassword: 'Nueva-Clave-77x' })
  })
})
