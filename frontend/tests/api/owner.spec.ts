import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// F14 ART-003: el cliente del panel del propietario; las lecturas llevan el rango, las escrituras el token CSRF y el id va codificado
describe('api client: panel del propietario', () => {
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
  const params = () => new URL(last()[0], 'http://x').searchParams

  it.each([
    ['ownerAi', '/api/owner/ai'],
    ['ownerAccounts', '/api/owner/accounts'],
    ['ownerUsage', '/api/owner/usage'],
  ] as const)('%s asks for the report of the chosen range with GET', async (method, path) => {
    fetchMock.mockResolvedValue(respond(200, {}))

    await api[method](90)

    expect(new URL(last()[0], 'http://x').pathname).toBe(path)
    expect(params().get('range')).toBe('90')
    expect(last()[1].method ?? 'GET').toBe('GET')
  })

  it('ownerPrices and ownerHealth take no range', async () => {
    fetchMock.mockResolvedValue(respond(200, {}))

    await api.ownerPrices()
    expect(new URL(last()[0], 'http://x').pathname).toBe('/api/owner/prices')
    expect(params().has('range')).toBe(false)
    await api.ownerHealth()
    expect(new URL(last()[0], 'http://x').pathname).toBe('/api/owner/health')
  })

  it('ownerUsers sends the filters, the order and the page and leaves out what is not set', async () => {
    fetchMock.mockResolvedValue(respond(200, { items: [], total: 0, page: 0, size: 25 }))

    await api.ownerUsers({ kind: 'MINOR', status: 'ACTIVE', sort: 'spend', dir: 'desc', page: 2, size: 50 })

    expect(new URL(last()[0], 'http://x').pathname).toBe('/api/owner/users')
    expect(Object.fromEntries(params())).toEqual({ kind: 'MINOR', status: 'ACTIVE', sort: 'spend', dir: 'desc', page: '2', size: '50' })

    await api.ownerUsers({})
    expect([...params().keys()]).toEqual([])
  })

  it('never puts a name, an email or a limit in the query string', async () => {
    fetchMock.mockResolvedValue(respond(200, { items: [] }))

    await api.ownerUsers({ kind: 'ADULT', sort: 'created', dir: 'asc', page: 0, size: 25 })

    expect(last()[0]).not.toMatch(/@|nombre|email|limit/i)
  })

  it.each([
    ['ownerDeactivate', 'deactivate'],
    ['ownerReactivate', 'reactivate'],
    ['ownerRevoke', 'revoke-sessions'],
  ] as const)('%s posts to the encoded user address with the CSRF token and no body', async (method, suffix) => {
    fetchMock.mockResolvedValue(respond(204))

    await api[method]('u/1')

    const [url, init] = last()
    expect(url).toBe(`/api/owner/users/u%2F1/${suffix}`)
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.body).toBeUndefined()
  })

  it('ownerSetLimit puts the amount as a number with the CSRF token', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.ownerSetLimit('u1', 7.5)

    const [url, init] = last()
    expect(url).toBe('/api/owner/users/u1/limit')
    expect(init.method).toBe('PUT')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(JSON.parse(init.body as string)).toEqual({ limitUsd: 7.5 })
  })

  it('ownerClearLimit sends DELETE to the limit address with the CSRF token', async () => {
    fetchMock.mockResolvedValue(respond(204))

    await api.ownerClearLimit('u1')

    const [url, init] = last()
    expect(url).toBe('/api/owner/users/u1/limit')
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it.each([[404, undefined], [429, 'OWNER_RATE_LIMIT'], [400, 'CANNOT_TARGET_SELF']])('turns a %s answer into an ApiError that carries the status and the code', async (status, code) => {
    fetchMock.mockResolvedValue(respond(status, code ? { code } : {}))

    const error = await api.ownerDeactivate('u1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(status)
    if (code) expect(error.code).toBe(code)
  })
})
