import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      getGuardianOverview: vi.fn(), getGuardianAlerts: vi.fn(), getGuardianConversations: vi.fn(), me: vi.fn(),
      getPhotoPermission: vi.fn(), setPhotoPermission: vi.fn(),
    },
  }
})
import { ApiError, api } from '@/api/client'
import GuardianStudentView from '@/views/GuardianStudentView.vue'
import { adult, byId, mountApp } from '../helpers'

const overview = {
  student: { id: 's1', displayName: 'Hugo', active: true },
  alerts: { last30Days: 0 },
  usage: { month: '2026-10', spendUsd: 0, limitUsd: 2, reached: false, byTask: [] },
  courses: [],
}
const open = () => mountApp(GuardianStudentView, { user: adult, route: '/alumnos/s1' })

// F19 AC-11b, AC-14: el tutor decide por menor si puede subir fotos de apuntes; el texto dice el riesgo y los errores no enseñan códigos
describe('Panel del alumno: permiso de fotos', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getGuardianOverview).mockResolvedValue(overview as never)
    vi.mocked(api.getGuardianAlerts).mockResolvedValue([] as never)
    vi.mocked(api.getGuardianConversations).mockResolvedValue([] as never)
    vi.mocked(api.getPhotoPermission).mockResolvedValue({ enabled: false })
    vi.mocked(api.setPhotoPermission).mockImplementation(async (_id: string, enabled: boolean) => ({ enabled }))
  })

  it('reads the permission of the student of the route and shows it off by default', async () => {
    const m = await open()

    expect(api.getPhotoPermission).toHaveBeenCalledWith('s1')
    const sw = byId(m.wrapper, 'photo-permission-switch')
    expect(sw.attributes('role')).toBe('switch')
    expect(sw.attributes('aria-checked')).toBe('false')
  })

  it('explains that the photo goes to an AI service and cannot be filtered before it is sent', async () => {
    const m = await open()

    const note = byId(m.wrapper, 'photo-permission-note').text()
    expect(note).toContain('servicio de IA')
    expect(note).toContain('filtrar')
  })

  it('turns it on with one PUT and shows the new state', async () => {
    const m = await open()

    await byId(m.wrapper, 'photo-permission-switch').trigger('click')
    await flushPromises()

    expect(api.setPhotoPermission).toHaveBeenCalledTimes(1)
    expect(api.setPhotoPermission).toHaveBeenCalledWith('s1', true)
    expect(byId(m.wrapper, 'photo-permission-switch').attributes('aria-checked')).toBe('true')
  })

  it('shows the permission already on when the server says so', async () => {
    vi.mocked(api.getPhotoPermission).mockResolvedValue({ enabled: true })
    const m = await open()

    expect(byId(m.wrapper, 'photo-permission-switch').attributes('aria-checked')).toBe('true')
  })

  it('keeps the old state and says it in words when the change fails, without any code', async () => {
    vi.mocked(api.setPhotoPermission).mockRejectedValue(new ApiError(409, 'PHOTOS_DISABLED'))
    const m = await open()

    await byId(m.wrapper, 'photo-permission-switch').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'photo-permission-switch').attributes('aria-checked')).toBe('false')
    const error = byId(m.wrapper, 'photo-permission-error')
    expect(error.exists()).toBe(true)
    expect(error.text()).not.toContain('PHOTOS_DISABLED')
    expect(error.text()).not.toContain('409')
  })

  it('treats a 404 as «no longer your student»', async () => {
    vi.mocked(api.getPhotoPermission).mockRejectedValue(new ApiError(404))
    const m = await open()

    expect(byId(m.wrapper, 'photo-permission-error').text().toLowerCase()).toContain('alumno')
    expect(byId(m.wrapper, 'photo-permission-switch').exists()).toBe(false)
  })

  it('does not break the rest of the panel when the permission cannot be read', async () => {
    vi.mocked(api.getPhotoPermission).mockRejectedValue(new ApiError(500))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-name').text()).toContain('Hugo')
  })
})
