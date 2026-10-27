import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, topicImagePath } from '@/api/client'

function respond(status: number, body?: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } })
}

// F12 AC-3, AC-12, AC-13: el cliente de las ilustraciones; lo que cambia datos lleva el token CSRF y los identificadores van codificados
describe('api client: ilustraciones de portada', () => {
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

  it('asks the server whether illustrations are offered and at what price', async () => {
    fetchMock.mockResolvedValue(respond(200, { available: true, pricePerImageUsd: 0.011, maxTopics: 12 }))

    const offer = await api.getIllustrationOffer()

    expect(last()[0]).toBe('/api/illustrations/offer')
    expect(offer).toEqual({ available: true, pricePerImageUsd: 0.011, maxTopics: 12 })
  })

  it('sends the switch when the course is created with illustrations, and does not send it otherwise', async () => {
    fetchMock.mockResolvedValue(respond(201, { id: 'c1' }))

    await api.createCourse('i1', [{ questionId: 'q1', optionId: 'o1' }], true)
    expect(JSON.parse(last()[1].body as string)).toEqual({ intakeId: 'i1', answers: [{ questionId: 'q1', optionId: 'o1' }], illustrations: true })

    await api.createCourse('i1', [{ questionId: 'q1', optionId: 'o1' }])
    expect(JSON.parse(last()[1].body as string)).toEqual({ intakeId: 'i1', answers: [{ questionId: 'q1', optionId: 'o1' }] })
  })

  it('regenerates the image of a topic with POST and the CSRF token, encoding the identifiers', async () => {
    fetchMock.mockResolvedValue(respond(202, { id: 'c/1' }))

    await api.regenerateTopicImage('c/1', 't 1')

    expect(last()[0]).toBe('/api/courses/c%2F1/topics/t%201/image/regenerate')
    expect(last()[1].method).toBe('POST')
    expect(last()[1].headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it('removes the image of a topic with DELETE and the CSRF token', async () => {
    fetchMock.mockResolvedValue(respond(200, { id: 'c1' }))

    await api.removeTopicImage('c1', 't1')

    expect(last()[0]).toBe('/api/courses/c1/topics/t1/image')
    expect(last()[1].method).toBe('DELETE')
    expect(last()[1].headers['X-XSRF-TOKEN']).toBe('token-csrf-123')
  })

  it('builds the path of the image of a topic, always on the own origin and with the identifiers encoded', () => {
    expect(topicImagePath('c1', 't1')).toBe('/api/courses/c1/topics/t1/image')
    expect(topicImagePath('c/1', 't 1')).toBe('/api/courses/c%2F1/topics/t%201/image')
    expect(topicImagePath('c1', 't1').startsWith('/api/')).toBe(true)
    expect(topicImagePath('//evil.com', 'x')).not.toMatch(/^\/\//)
  })
})
