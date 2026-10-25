import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getGuardianThread: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import GuardianThreadView from '@/views/GuardianThreadView.vue'
import { adult, byId, mountApp } from '../helpers'

const message = (over: object = {}) => ({ id: 'm1', role: 'USER', status: 'OK', content: 'Una duda', createdAt: '2026-10-25T10:00:00Z', ...over })

const open = () => mountApp(GuardianThreadView, { user: adult, route: '/alumnos/s1/cursos/c1/lecciones/p1' })

// AC-5, AC-11, AC-12: la conversación de un alumno con el tutor IA, en solo lectura
describe('Panel de la conversación de un alumno', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getGuardianThread).mockResolvedValue({ messages: [
      message({ id: 'a', content: 'Mi duda' }),
      message({ id: 'b', role: 'TUTOR', content: 'Una explicación con **negrita** y [1].', sources: [{ n: 1, page: 3 }] }),
    ] } as never)
  })

  it('loads the thread of the student, course and lesson of the route', async () => {
    await open()

    expect(api.getGuardianThread).toHaveBeenCalledWith('s1', 'c1', 'p1')
  })

  it('shows the student messages as text and the tutor ones with format and its sources', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-msg-a').text()).toContain('Mi duda')
    expect(byId(m.wrapper, 'guardian-msg-b').find('strong').text()).toBe('negrita')
    expect(byId(m.wrapper, 'guardian-msg-b').text()).toContain('Fuente 1')
    expect(byId(m.wrapper, 'guardian-msg-b').text()).toContain('3')
  })

  it('marks what the filter blocked and what it replaced', async () => {
    vi.mocked(api.getGuardianThread).mockResolvedValue({ messages: [
      message({ id: 'a', status: 'BLOCKED', content: 'una pregunta bloqueada' }),
      message({ id: 'b', role: 'TUTOR', status: 'BLOCKED', content: 'Contenido inapropiado. Consulta con tu adulto.' }),
      message({ id: 'c', content: 'otra' }),
      message({ id: 'd', role: 'TUTOR', status: 'FILTERED', content: 'No puedo ayudarte con eso. Consulta con tu adulto.' }),
    ] } as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-msg-a').attributes('data-status')).toBe('BLOCKED')
    expect(byId(m.wrapper, 'guardian-msg-a').text()).toContain('una pregunta bloqueada') // el adulto ve lo que se preguntó
    expect(byId(m.wrapper, 'guardian-msg-a').text().toLowerCase()).toContain('bloquead')
    expect(byId(m.wrapper, 'guardian-msg-d').attributes('data-status')).toBe('FILTERED')
    expect(byId(m.wrapper, 'guardian-msg-d').text().toLowerCase()).toContain('filtrad')
    expect(byId(m.wrapper, 'guardian-msg-c').attributes('data-status')).toBe('OK')
  })

  it('keeps the messages in the order the server sends them', async () => {
    const m = await open()

    const html = m.wrapper.html()
    expect(html.indexOf('guardian-msg-a')).toBeLessThan(html.indexOf('guardian-msg-b'))
  })

  it('says so when the lesson has no conversation', async () => {
    vi.mocked(api.getGuardianThread).mockResolvedValue({ messages: [] } as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-thread-empty').exists()).toBe(true)
  })

  it('renders hostile text of the student and of the tutor as plain text', async () => {
    vi.mocked(api.getGuardianThread).mockResolvedValue({ messages: [
      message({ id: 'a', content: '<img src=x onerror=alert(1)>' }),
      message({ id: 'b', role: 'TUTOR', content: '<script>alert(1)</script> y [clic](javascript:alert(1))' }),
    ] } as never)
    const m = await open()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.find('script').exists()).toBe(false)
    expect(m.wrapper.find('a[href^="javascript"]').exists()).toBe(false)
    expect(byId(m.wrapper, 'guardian-msg-a').text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('is read only: no box to write, no send button and no way to empty the thread', async () => {
    const m = await open()

    expect(m.wrapper.find('textarea').exists()).toBe(false)
    expect(m.wrapper.find('input').exists()).toBe(false)
    expect(m.wrapper.findAll('button')).toHaveLength(0)
    expect(m.wrapper.text().toLowerCase()).toContain('solo lectura')
  })

  it('has a way back to the student', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-back').attributes('href')).toBe('/alumnos/s1')
  })

  it('shows a not-found message when the conversation is not of that student', async () => {
    vi.mocked(api.getGuardianThread).mockRejectedValue(new ApiError(404))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-not-found').exists()).toBe(true)
  })

  it('shows a generic error, never the code, when it fails', async () => {
    vi.mocked(api.getGuardianThread).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-error').exists()).toBe(true)
    expect(m.wrapper.text()).not.toContain('INTERNAL')
  })
})
