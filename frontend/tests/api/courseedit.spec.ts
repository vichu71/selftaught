import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// AC-1 a AC-12, AC-14: el cliente de edición y de compartir; todo lo que cambia datos lleva el token CSRF y los identificadores van codificados
describe('api client: edición y compartir de cursos', () => {
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
  const body = () => JSON.parse(last()[1].body as string)

  type Call = [string, () => Promise<unknown>, string, string, unknown?]
  const calls: Call[] = [
    ['renameTopic', () => api.renameTopic('c/1', 't 1', { title: 'T', summary: 'S' }), 'PUT', '/api/courses/c%2F1/topics/t%201', { title: 'T', summary: 'S' }],
    ['reorderTopics', () => api.reorderTopics('c1', ['t2', 't1']), 'PUT', '/api/courses/c1/topics/order', { ids: ['t2', 't1'] }],
    ['removeTopic', () => api.removeTopic('c1', 't1'), 'DELETE', '/api/courses/c1/topics/t1'],
    ['addTopic', () => api.addTopic('c1', { title: 'T', summary: 'S', chapter: { title: 'C', objective: 'O' } }), 'POST', '/api/courses/c1/topics',
      { title: 'T', summary: 'S', chapter: { title: 'C', objective: 'O' } }],
    ['renameChapter', () => api.renameChapter('c1', 'ch1', { title: 'C', objective: 'O' }), 'PUT', '/api/courses/c1/chapters/ch1', { title: 'C', objective: 'O' }],
    ['reorderChapters', () => api.reorderChapters('c1', 't1', ['b', 'a']), 'PUT', '/api/courses/c1/topics/t1/chapters/order', { ids: ['b', 'a'] }],
    ['removeChapter', () => api.removeChapter('c1', 'ch1'), 'DELETE', '/api/courses/c1/chapters/ch1'],
    ['addChapter', () => api.addChapter('c1', 't1', { title: 'C', objective: 'O' }), 'POST', '/api/courses/c1/topics/t1/chapters', { title: 'C', objective: 'O' }],
    ['editLesson', () => api.editLesson('c1', 'p1', '# Texto'), 'PUT', '/api/courses/c1/pieces/p1/content', { markdown: '# Texto' }],
    ['regeneratePiece', () => api.regeneratePiece('c1', 'p1'), 'POST', '/api/courses/c1/pieces/p1/regenerate'],
    ['listShareCodes', () => api.listShareCodes('c1'), 'GET', '/api/courses/c1/share'],
    ['revokeShareCode', () => api.revokeShareCode('c1', 'k 1'), 'DELETE', '/api/courses/c1/share/k%201'],
    ['redeemCode', () => api.redeemCode('ABCD-EFGH-JKLM-NPQR'), 'POST', '/api/courses/redeem', { code: 'ABCD-EFGH-JKLM-NPQR' }],
  ]

  it.each(calls)('%s uses the right method and address, with CSRF when it changes data', async (_name, run, method, url, expectedBody) => {
    fetchMock.mockResolvedValue(respond(method === 'DELETE' ? 204 : 200, {}))

    await run()

    const [called, init] = last()
    expect(called).toBe(url)
    expect(init.method ?? 'GET').toBe(method)
    if (method === 'GET') expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
    else expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    if (expectedBody === undefined) expect(init.body).toBeUndefined()
    else expect(body()).toEqual(expectedBody)
  })

  it('creates a share code with the chosen days, or with an empty body to use the default', async () => {
    fetchMock.mockResolvedValue(respond(201, { id: 'k1', code: 'AAAA-AAAA-AAAA-AAAA', expiresAt: '2026-11-01T00:00:00Z' }))

    const created = await api.createShareCode('c1', 30)
    expect(last()[0]).toBe('/api/courses/c1/share')
    expect(last()[1].method).toBe('POST')
    expect(body()).toEqual({ days: 30 })
    expect(created.code).toBe('AAAA-AAAA-AAAA-AAAA')

    await api.createShareCode('c1')
    expect(body()).toEqual({})
  })

  it('exposes the status and code of the errors the editor answers', async () => {
    fetchMock.mockResolvedValue(respond(409, { code: 'COURSE_BUSY', message: 'x' }))

    await expect(api.renameTopic('c1', 't1', { title: 'T', summary: 'S' })).rejects.toMatchObject({ status: 409, code: 'COURSE_BUSY' })
    await expect(api.renameTopic('c1', 't1', { title: 'T', summary: 'S' })).rejects.toBeInstanceOf(ApiError)
  })
})
