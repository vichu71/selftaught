import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// AC-1, AC-12: el cliente de borrado; lleva el token CSRF, codifica el id y no inventa el cuerpo
describe('api client: borrar cursos', () => {
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

  const last = () => fetchMock.mock.calls.at(-1) as [string, RequestInit & { headers: Record<string, string> }]

  it('deleteCourse sends DELETE to the encoded course address with the CSRF token and no body', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.deleteCourse('c/1')

    const [url, init] = last()
    expect(url).toBe('/api/courses/c%2F1')
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.body).toBeUndefined()
  })

  it('deleteCourses posts the ids to the bulk address with the CSRF token', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.deleteCourses(['c1', 'c2'])

    const [url, init] = last()
    expect(url).toBe('/api/courses/delete')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(JSON.parse(init.body as string)).toEqual({ ids: ['c1', 'c2'] })
  })

  it.each([[409, 'COURSE_BUSY'], [404, undefined], [403, 'CHILD_CANNOT_DELETE']])('turns a %s answer into an ApiError that carries the status and the code', async (status, code) => {
    fetchMock.mockResolvedValue(respond(status, code ? { code } : {}))

    const error = await api.deleteCourses(['c1']).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(status)
    expect(error.code).toBe(code)
  })
})
