import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/api/client'

function respond(status: number, body?: unknown) {
  return status === 204
    ? new Response(null, { status })
    : new Response(JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api client: courses', () => {
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

  // AC-3
  it('starts an intake with the topic and the chosen documents as JSON, with the CSRF header', async () => {
    fetchMock.mockResolvedValue(respond(201, { intakeId: 'i1', questions: [] }))

    const result = await api.startIntake('Inglés de 2º de la ESO', ['d1', 'd2'])

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/courses/intake')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ topic: 'Inglés de 2º de la ESO', documentIds: ['d1', 'd2'] })
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
    expect(init.credentials).toBe('same-origin')
    expect(result).toEqual({ intakeId: 'i1', questions: [] })
  })

  it('never puts the topic in the URL', async () => {
    fetchMock.mockResolvedValue(respond(201, { intakeId: 'i1', questions: [] }))

    await api.startIntake('tema privado', [])

    expect(String(fetchMock.mock.calls[0][0])).not.toContain('privado')
  })

  // AC-5
  it('creates the course with the intake id and the answers', async () => {
    fetchMock.mockResolvedValue(respond(201, { id: 'c1' }))

    await api.createCourse('i1', [{ questionId: 'q1', optionId: 'o2' }])

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/courses')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ intakeId: 'i1', answers: [{ questionId: 'q1', optionId: 'o2' }] })
    expect(init.headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it('lists the courses and reads one by id with plain GETs, encoding the id', async () => {
    fetchMock.mockResolvedValue(respond(200, []))
    await api.listCourses()
    fetchMock.mockResolvedValue(respond(200, { id: 'a/b' }))
    await api.getCourse('a/b')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/courses')
    expect(fetchMock.mock.calls[0][1].method ?? 'GET').toBe('GET')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/courses/a%2Fb')
    expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  // AC-8: el código llega al cliente para poder mostrar su mensaje, nunca el motivo
  it('exposes the status and code of a blocked topic', async () => {
    fetchMock.mockResolvedValue(respond(422, { code: 'TOPIC_NOT_ALLOWED', message: 'x' }))

    await expect(api.startIntake('algo', [])).rejects.toMatchObject({ status: 422, code: 'TOPIC_NOT_ALLOWED' })
    await expect(api.startIntake('algo', [])).rejects.toBeInstanceOf(ApiError)
  })
})
