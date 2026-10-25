import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { getGuardianOverview: vi.fn(), getGuardianAlerts: vi.fn(), getGuardianConversations: vi.fn(), me: vi.fn() },
  }
})
import { ApiError, api } from '@/api/client'
import GuardianStudentView from '@/views/GuardianStudentView.vue'
import { adult, byId, mountApp } from '../helpers'

const overview = (over: object = {}) => ({
  student: { id: 's1', displayName: 'Hugo', active: true },
  lastActivityAt: '2026-10-25T10:00:00Z',
  alerts: { last30Days: 2 },
  usage: { month: '2026-10', spendUsd: 0.42, limitUsd: 2, reached: false, byTask: [{ task: 'LESSON', spendUsd: 0.3, calls: 4 }, { task: 'TUTOR', spendUsd: 0.12, calls: 6 }] },
  courses: [
    { id: 'c1', title: 'Historia', status: 'READY', percent: 62, lessonsDone: 5, lessonsTotal: 8, examsPassed: 1, examsTotal: 2, createdAt: '2026-10-20T10:00:00Z' },
    { id: 'c2', title: 'Fracciones', status: 'GENERATING', lessonsDone: 0, lessonsTotal: 0, examsPassed: 0, examsTotal: 0, createdAt: '2026-10-24T10:00:00Z' },
  ],
  ...over,
})

const alert = (over: object = {}) => ({ type: 'TUTOR_QUESTION_BLOCKED', at: '2026-10-25T09:00:00Z', courseId: 'c1', courseTitle: 'Historia', pieceId: 'p1', lessonTitle: 'Capítulo 1.1', ...over })

const conversation = (over: object = {}) => ({ courseId: 'c1', courseTitle: 'Historia', pieceId: 'p1', lessonTitle: 'Capítulo 1.1', messages: 12, blocked: 1, filtered: 0, lastAt: '2026-10-25T09:00:00Z', ...over })

const open = () => mountApp(GuardianStudentView, { user: adult, route: '/alumnos/s1' })

// AC-1, AC-2, AC-6, AC-7, AC-12: la pantalla de un alumno
describe('Panel del alumno', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getGuardianOverview).mockResolvedValue(overview() as never)
    vi.mocked(api.getGuardianAlerts).mockResolvedValue([alert()] as never)
    vi.mocked(api.getGuardianConversations).mockResolvedValue([conversation()] as never)
  })

  it('loads the three things of the student of the route and shows the name', async () => {
    const m = await open()

    expect(api.getGuardianOverview).toHaveBeenCalledWith('s1')
    expect(api.getGuardianAlerts).toHaveBeenCalledWith('s1')
    expect(api.getGuardianConversations).toHaveBeenCalledWith('s1')
    expect(byId(m.wrapper, 'guardian-name').text()).toContain('Hugo')
  })

  // ---------- cursos ----------

  it('lists the courses with their state and progress and links each to its page', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-course-c1').text()).toContain('Historia')
    expect(byId(m.wrapper, 'guardian-course-c1').text()).toContain('62')
    expect(byId(m.wrapper, 'guardian-course-link-c1').attributes('href')).toBe('/alumnos/s1/cursos/c1')
    expect(byId(m.wrapper, 'guardian-course-c2').text()).toContain('Fracciones')
  })

  it('says so when the student has no courses', async () => {
    vi.mocked(api.getGuardianOverview).mockResolvedValue(overview({ courses: [], lastActivityAt: undefined }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-courses-empty').exists()).toBe(true)
    expect(byId(m.wrapper, 'guardian-last-activity').exists()).toBe(false)
  })

  it('marks a deactivated student and still shows their data', async () => {
    vi.mocked(api.getGuardianOverview).mockResolvedValue(overview({ student: { id: 's1', displayName: 'Hugo', active: false } }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-inactive').exists()).toBe(true)
    expect(byId(m.wrapper, 'guardian-course-c1').exists()).toBe(true)
  })

  // ---------- avisos ----------

  it('shows the alerts first, each with a readable type, the place and a link to the conversation', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-alerts').exists()).toBe(true)
    expect(byId(m.wrapper, 'guardian-alert-0').text().toLowerCase()).toContain('pregunta bloqueada')
    expect(byId(m.wrapper, 'guardian-alert-0').text()).toContain('Historia')
    expect(byId(m.wrapper, 'guardian-alert-link-0').attributes('href')).toBe('/alumnos/s1/cursos/c1/lecciones/p1')
    const html = m.wrapper.html()
    expect(html.indexOf('guardian-alerts')).toBeGreaterThan(-1)
    expect(html.indexOf('guardian-alerts')).toBeLessThan(html.indexOf('guardian-course-c1')) // los avisos van antes que los cursos
  })

  it.each([
    ['TUTOR_ANSWER_FILTERED', 'respuesta del tutor'],
    ['COURSE_TOPIC_BLOCKED', 'tema'],
    ['STUDY_ANSWER_BLOCKED', 'ejercicio'],
    ['COURSE_CONTENT_BLOCKED', 'contenido'],
  ])('describes the %s alert in plain words', async (type, fragment) => {
    vi.mocked(api.getGuardianAlerts).mockResolvedValue([alert({ type, topic: 'un tema', category: 'VIOLENCE', pieceId: undefined, lessonTitle: undefined })] as never)
    const m = await open()

    const text = byId(m.wrapper, 'guardian-alert-0').text().toLowerCase()
    expect(text).toContain(fragment)
    expect(text).not.toContain(type.toLowerCase())
    expect(text).not.toContain('tutor_')
  })

  it('shows the topic of a rejected course as plain text', async () => {
    vi.mocked(api.getGuardianAlerts).mockResolvedValue([alert({ type: 'COURSE_TOPIC_BLOCKED', topic: '<img src=x onerror=alert(1)>', category: 'VIOLENCE' })] as never)
    const m = await open()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(byId(m.wrapper, 'guardian-alert-0').text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('says there is nothing to warn about when there are no alerts', async () => {
    vi.mocked(api.getGuardianAlerts).mockResolvedValue([] as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-alerts-empty').exists()).toBe(true)
  })

  // ---------- conversaciones ----------

  it('lists the conversations with their counts and a link to each thread', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'guardian-conversation-p1').text()).toContain('Capítulo 1.1')
    expect(byId(m.wrapper, 'guardian-conversation-p1').text()).toContain('12')
    expect(byId(m.wrapper, 'guardian-conversation-p1').text().toLowerCase()).toContain('bloquead')
    expect(byId(m.wrapper, 'guardian-conversation-link-p1').attributes('href')).toBe('/alumnos/s1/cursos/c1/lecciones/p1')
  })

  it('says so when there are no conversations', async () => {
    vi.mocked(api.getGuardianConversations).mockResolvedValue([] as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-conversations-empty').exists()).toBe(true)
  })

  // ---------- consumo ----------

  it('shows the month spend against the limit and the split by task without tokens', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'guardian-usage').text()
    expect(text).toMatch(/0[,.]42/)
    expect(text).toMatch(/2[,.]00|\b2\b/)
    expect(text.toLowerCase()).toContain('lecciones')
    expect(text.toLowerCase()).toContain('tutor')
    expect(text.toLowerCase()).not.toContain('token')
    expect(text).not.toContain('LESSON')
  })

  it('warns when the student reached their limit', async () => {
    vi.mocked(api.getGuardianOverview).mockResolvedValue(overview({ usage: { month: '2026-10', spendUsd: 2, limitUsd: 2, reached: true, byTask: [] } }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'guardian-usage-reached').exists()).toBe(true)
  })

  // ---------- errores y solo lectura ----------

  it('shows a plain not-found message for a student who is not theirs', async () => {
    vi.mocked(api.getGuardianOverview).mockRejectedValue(new ApiError(404))
    vi.mocked(api.getGuardianAlerts).mockRejectedValue(new ApiError(404))
    vi.mocked(api.getGuardianConversations).mockRejectedValue(new ApiError(404))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-not-found').exists()).toBe(true)
    expect(m.wrapper.text()).not.toContain('404')
  })

  it('shows a generic error, never the code, when something fails', async () => {
    vi.mocked(api.getGuardianAlerts).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const m = await open()

    expect(byId(m.wrapper, 'guardian-error').exists()).toBe(true)
    expect(m.wrapper.text()).not.toContain('INTERNAL')
    expect(byId(m.wrapper, 'guardian-course-c1').exists()).toBe(true)
  })

  it('offers no way to change anything: no inputs and no buttons that write', async () => {
    const m = await open()

    expect(m.wrapper.find('textarea').exists()).toBe(false)
    expect(m.wrapper.find('input').exists()).toBe(false)
    expect(m.wrapper.findAll('button').every((b) => /volver|cerrar/i.test(b.text()))).toBe(true)
  })
})
