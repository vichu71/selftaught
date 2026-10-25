import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// AC-8 y AC-13: el cliente del panel solo lee (GET sin CSRF), codifica los identificadores y no tiene ningún método que modifique
describe('api client: panel del adulto tutor', () => {
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

  it.each([
    ['getGuardianOverview', ['s/1'], '/api/tutor/students/s%2F1/overview'],
    ['getGuardianCourse', ['s/1', 'c 1'], '/api/tutor/students/s%2F1/courses/c%201'],
    ['getGuardianAttempts', ['s1', 'c1'], '/api/tutor/students/s1/courses/c1/attempts'],
    ['getGuardianConversations', ['s1'], '/api/tutor/students/s1/conversations'],
    ['getGuardianThread', ['s1', 'c1', 'p/1'], '/api/tutor/students/s1/courses/c1/pieces/p%2F1/tutor'],
    ['getGuardianAlerts', ['s1'], '/api/tutor/students/s1/alerts'],
  ])('%s reads with a plain GET on the right address', async (method, args, url) => {
    fetchMock.mockResolvedValue(respond(200, []))

    await (api as unknown as Record<string, (...a: string[]) => Promise<unknown>>)[method](...(args as string[]))

    const [called, init] = last()
    expect(called).toBe(url)
    expect(init.method ?? 'GET').toBe('GET')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
    expect(init.body).toBeUndefined()
  })

  it('has no method that writes anything of a student', () => {
    const names = Object.keys(api).filter((n) => /guardian/i.test(n))

    expect(names.sort()).toEqual(['getGuardianAlerts', 'getGuardianAttempts', 'getGuardianConversations', 'getGuardianCourse', 'getGuardianOverview', 'getGuardianThread'])
  })

  it('exposes the status of the errors the panel answers', async () => {
    fetchMock.mockResolvedValue(respond(404, { code: 'NOT_FOUND', message: 'x' }))

    await expect(api.getGuardianOverview('s1')).rejects.toMatchObject({ status: 404 })
    await expect(api.getGuardianOverview('s1')).rejects.toBeInstanceOf(ApiError)
  })
})
