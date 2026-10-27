import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// F15 ART-003: el cliente del gasto por tarea; solo lectura, sin parámetros (el usuario es el de la sesión)
describe('api client: gasto por tarea', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  const last = () => fetchMock.mock.calls.at(-1) as [string, RequestInit]

  it('getUsageTasks asks the tasks address with GET, no body and no parameters', async () => {
    fetchMock.mockResolvedValue(respond(200, { month: '2026-10', tasks: [{ task: 'LESSON', calls: 2, spendUsd: 0.4 }] }))

    const result = await api.getUsageTasks()

    const [url, init] = last()
    expect(url).toBe('/api/ai/usage/tasks')
    expect(init.method).toBe('GET')
    expect(init.body).toBeUndefined()
    expect(result.tasks[0]).toEqual({ task: 'LESSON', calls: 2, spendUsd: 0.4 })
  })

  it('turns an error into an ApiError with its status', async () => {
    fetchMock.mockResolvedValue(respond(401))

    const error = await api.getUsageTasks().catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(401)
  })
})
