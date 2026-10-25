import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// AC-1, AC-9 y AC-13: el cliente del tutor; todo lo que modifica lleva el token CSRF y los identificadores van codificados
describe('api client: tutor', () => {
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

  it('reads the thread of a lesson with a plain GET, encoding both ids', async () => {
    fetchMock.mockResolvedValue(respond(200, { canClear: true, messages: [] }))

    const thread = await api.getTutorThread('c/1', 'p 1')

    const [url, init] = last()
    expect(url).toBe('/api/courses/c%2F1/pieces/p%201/tutor')
    expect(init.method ?? 'GET').toBe('GET')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
    expect(thread.canClear).toBe(true)
  })

  it('asks a question with a POST that carries the CSRF header and only the question', async () => {
    fetchMock.mockResolvedValue(respond(201, { user: { id: 'u1' }, tutor: { id: 't1' } }))

    const result = await api.askTutor('c1', 'p1', '¿Qué es una variable?')

    const [url, init] = last()
    expect(url).toBe('/api/courses/c1/pieces/p1/tutor')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(JSON.parse(init.body as string)).toEqual({ question: '¿Qué es una variable?' })
    expect(result.tutor.id).toBe('t1')
  })

  it('empties the thread with a DELETE that carries the CSRF header and no body', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.clearTutor('c1', 'p1')

    const [url, init] = last()
    expect(url).toBe('/api/courses/c1/pieces/p1/tutor')
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.body).toBeUndefined()
  })

  it('exposes the status and code of the errors the tutor answers', async () => {
    fetchMock.mockResolvedValue(respond(429, { code: 'TUTOR_RATE_LIMITED', message: 'x' }))

    await expect(api.askTutor('c1', 'p1', 'x')).rejects.toMatchObject({ status: 429, code: 'TUTOR_RATE_LIMITED' })
    await expect(api.askTutor('c1', 'p1', 'x')).rejects.toBeInstanceOf(ApiError)
  })
})
