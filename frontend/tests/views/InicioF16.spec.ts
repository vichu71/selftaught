import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      listStudents: vi.fn(), createStudent: vi.fn(), resetStudentPassword: vi.fn(), deactivateStudent: vi.fn(), reactivateStudent: vi.fn(),
      listCourses: vi.fn(), getStudyProgress: vi.fn(), logout: vi.fn(), me: vi.fn(),
    },
  }
})
import { api } from '@/api/client'
import HomeView from '@/views/HomeView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const hugo = { id: 's1', displayName: 'Hugo', loginId: 'hugo-4k7', status: 'ACTIVE' }
const lola = { id: 's2', displayName: 'Lola', loginId: 'lola', status: 'DEACTIVATED' }

// F16 AC-1 a AC-3, AC-8 (D1 a D3, D7): atajos, menores como tarjetas, alta plegable y vacíos con ilustración
describe('Inicio', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([hugo, lola])
    vi.mocked(api.listCourses).mockResolvedValue([])
    vi.mocked(api.getStudyProgress).mockResolvedValue([])
  })

  // ---------- atajos (AC-1) ----------

  it('shows the three shortcuts as cards that are whole links, with their destinations and test ids', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'shortcut-grid').exists()).toBe(true)
    for (const [id, href, title] of [['material-link', '/material', 'Mi material'], ['redeem-link', '/codigo', 'Tengo un código'], ['results-link', '/resultados', 'Resultados']]) {
      const card = byId(wrapper, id)
      expect(card.element.tagName).toBe('A')
      expect(card.attributes('href')).toBe(href)
      expect(card.classes()).toContain('action-card')
      expect(card.text()).toContain(title)
      expect(card.find('svg[aria-hidden="true"]').exists()).toBe(true)
    }
  })

  it('has no loose underlined sentence for them any more', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(wrapper.text()).not.toContain('sube tus apuntes (PDF, Word o texto).')
  })

  it('describes Resultados without money for a minor and with it for an adult', async () => {
    const a = await mountApp(HomeView, { user: adult })
    const m = await mountApp(HomeView, { user: minor })

    expect(byId(a.wrapper, 'results-link').text().toLowerCase()).toContain('gast')
    expect(byId(m.wrapper, 'results-link').text().toLowerCase()).not.toContain('gast')
    expect(byId(m.wrapper, 'results-link').text().toLowerCase()).not.toContain('usd')
  })

  it('shows the three shortcuts to a minor too', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    for (const id of ['material-link', 'redeem-link', 'results-link']) expect(byId(wrapper, id).exists()).toBe(true)
  })

  // ---------- menores como tarjetas (AC-2) ----------

  it('shows each minor as a card with the buttons of their actions', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(wrapper.findAll('.student-card')).toHaveLength(2)
    expect(byId(wrapper, 'student-s1').text()).toContain('Hugo')
    expect(byId(wrapper, 'student-status-s1').text()).toContain('Activo')
    expect(byId(wrapper, 'student-status-s2').text()).toContain('De baja')
    expect(byId(wrapper, 'student-activity-s1').attributes('href')).toBe('/alumnos/s1')
    expect(byId(wrapper, 'reset-s1').exists()).toBe(true)
  })

  it('still deactivates and reactivates from the cards and refreshes the list', async () => {
    vi.mocked(api.deactivateStudent).mockResolvedValue(undefined as never)
    vi.mocked(api.reactivateStudent).mockResolvedValue(undefined as never)
    const { wrapper } = await mountApp(HomeView, { user: adult })

    await byId(wrapper, 'deactivate-s1').trigger('click')
    await flushPromises()
    await byId(wrapper, 'reactivate-s2').trigger('click')
    await flushPromises()

    expect(api.deactivateStudent).toHaveBeenCalledWith('s1')
    expect(api.reactivateStudent).toHaveBeenCalledWith('s2')
    expect(vi.mocked(api.listStudents).mock.calls.length).toBeGreaterThanOrEqual(3)
  })

  it('still changes a password from a card with the same messages', async () => {
    vi.mocked(api.resetStudentPassword).mockResolvedValue(undefined as never)
    const { wrapper } = await mountApp(HomeView, { user: adult })

    await byId(wrapper, 'reset-s1').trigger('click')
    await byId(wrapper, 'new-password').setValue('corta')
    await byId(wrapper, 'reset-confirm').trigger('click')
    expect(wrapper.text()).toContain('entre 10 y 128 caracteres')
    await byId(wrapper, 'new-password').setValue('una-clave-larga-1')
    await byId(wrapper, 'reset-confirm').trigger('click')
    await flushPromises()

    expect(api.resetStudentPassword).toHaveBeenCalledWith('s1', 'una-clave-larga-1')
    expect(byId(wrapper, 'reset-done').exists()).toBe(true)
  })

  it('shows no minors panel to a minor', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'students-panel').exists()).toBe(false)
    expect(wrapper.find('.student-card').exists()).toBe(false)
  })

  // ---------- alta plegable (AC-3) ----------

  it('folds the sign-up form into «Añadir un menor» when there are minors', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    const toggle = byId(wrapper, 'add-student-toggle')
    expect(toggle.text()).toContain('Añadir un menor')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect((byId(wrapper, 'create-form').element.closest('[id]') as HTMLElement).style.display).toBe('none')
  })

  it('opens and closes it with the button', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    await byId(wrapper, 'add-student-toggle').trigger('click')
    expect(byId(wrapper, 'add-student-toggle').attributes('aria-expanded')).toBe('true')
    expect((byId(wrapper, 'create-form').element.closest('[id]') as HTMLElement).style.display).not.toBe('none')
    await byId(wrapper, 'add-student-toggle').trigger('click')
    expect(byId(wrapper, 'add-student-toggle').attributes('aria-expanded')).toBe('false')
  })

  it('starts open, with a friendly note, when there are no minors', async () => {
    vi.mocked(api.listStudents).mockResolvedValue([])
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'add-student-toggle').attributes('aria-expanded')).toBe('true')
    expect(byId(wrapper, 'no-students').classes()).toContain('empty-note')
    expect(byId(wrapper, 'no-students').text()).toContain('Todavía no has dado de alta a nadie')
  })

  it('creates a minor from the form with the same validation and shows the generated access id', async () => {
    vi.mocked(api.createStudent).mockResolvedValue({ id: 's9', displayName: 'Nico', loginId: 'nico-7a2', status: 'ACTIVE' } as never)
    const { wrapper } = await mountApp(HomeView, { user: adult })
    await byId(wrapper, 'add-student-toggle').trigger('click')

    await byId(wrapper, 'display-name').setValue('Nico')
    await byId(wrapper, 'password').setValue('corta')
    await byId(wrapper, 'create-submit').trigger('click')
    await byId(wrapper, 'create-form').trigger('submit')
    await flushPromises()
    expect(byId(wrapper, 'create-error').text()).toContain('entre 10 y 128 caracteres')
    await byId(wrapper, 'password').setValue('una-clave-larga-1')
    await byId(wrapper, 'create-form').trigger('submit')
    await flushPromises()

    expect(api.createStudent).toHaveBeenCalled()
    expect(byId(wrapper, 'created-login-id').text()).toContain('nico-7a2')
  })

  // ---------- vacíos y texto plano (AC-8, AC-9) ----------

  it('shows a friendly note with the seed when there are no courses', async () => {
    const { wrapper } = await mountApp(HomeView, { user: adult })

    const note = byId(wrapper, 'no-courses')
    expect(note.classes()).toContain('empty-note')
    expect(note.text()).toContain('Todavía no tienes ningún curso')
    expect(note.find('svg').exists()).toBe(true)
  })

  it('renders a hostile minor name as plain text', async () => {
    vi.mocked(api.listStudents).mockResolvedValue([{ ...hugo, displayName: '<img src=x onerror=alert(1)>' }])
    const { wrapper } = await mountApp(HomeView, { user: adult })

    expect(byId(wrapper, 'student-s1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('keeps the notice to a minor that the tutor can see their activity, unchanged', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    const text = byId(wrapper, 'guardian-notice').text().toLowerCase()
    expect(text).toContain('respuestas')
    expect(text).toContain('intentos')
    expect(text).toContain('conversaciones')
  })
})
