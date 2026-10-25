import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api client: generation', () => {
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

  it('reads a piece of a course with a plain GET, encoding both ids', async () => {
    fetchMock.mockResolvedValue(respond(200, { id: 'p1' }))

    await api.getPiece('c/1', 'p 1')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/courses/c%2F1/pieces/p%201')
    expect(init.method ?? 'GET').toBe('GET')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('resumes a course with a POST that carries the CSRF header and no body', async () => {
    fetchMock.mockResolvedValue(respond(202, { id: 'c1', status: 'PENDING_GENERATION' }))

    const result = await api.resumeCourse('c1')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/courses/c1/resume')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.body).toBeUndefined()
    expect(result.status).toBe('PENDING_GENERATION')
  })

  it('exposes the status and code when resuming too soon', async () => {
    fetchMock.mockResolvedValue(respond(429, { code: 'RESUME_TOO_SOON', message: 'x' }))

    await expect(api.resumeCourse('c1')).rejects.toMatchObject({ status: 429, code: 'RESUME_TOO_SOON' })
    await expect(api.resumeCourse('c1')).rejects.toBeInstanceOf(ApiError)
  })

  it('reads the results and the usage with plain GET requests', async () => {
    fetchMock.mockResolvedValue(respond(200, { courses: [] }))

    await api.getResults()
    await api.getUsage()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/results')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/ai/usage')
    expect(fetchMock.mock.calls.every(([, init]) => (init.method ?? 'GET') === 'GET')).toBe(true)
  })

  it('points the export at the course export endpoint', () => {
    expect(api.exportUrl('c 1')).toBe('/api/courses/c%201/export')
  })
})
