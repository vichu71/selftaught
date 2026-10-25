import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      listStudents: vi.fn(), createStudent: vi.fn(), resetStudentPassword: vi.fn(),
      deactivateStudent: vi.fn(), reactivateStudent: vi.fn(), me: vi.fn(),
    },
  }
})
import { ApiError, api } from '@/api/client'
import StudentsPanel from '@/components/StudentsPanel.vue'
import { adult, byId, mountApp } from '../helpers'

const hugo = { id: 's1', displayName: 'Hugo', loginId: 'hugo-4k7', status: 'ACTIVE' }
const lola = { id: 's2', displayName: 'Lola', loginId: 'lola', status: 'DEACTIVATED' }

async function mountPanel() {
  return mountApp(StudentsPanel, { user: adult })
}

describe('StudentsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([hugo, lola])
  })

  // AC-5
  it('lists the minors with their login id and status', async () => {
    const { wrapper } = await mountPanel()

    expect(byId(wrapper, 'student-s1').text()).toContain('Hugo')
    expect(byId(wrapper, 'student-s1').text()).toContain('hugo-4k7')
    expect(byId(wrapper, 'student-s2').text()).toContain('Lola')
  })

  it('shows an empty-state message when there are no minors', async () => {
    vi.mocked(api.listStudents).mockResolvedValue([])
    const { wrapper } = await mountPanel()

    expect(byId(wrapper, 'no-students').exists()).toBe(true)
  })

  it('shows an error if the list cannot be loaded', async () => {
    vi.mocked(api.listStudents).mockRejectedValue(new ApiError(500))
    const { wrapper } = await mountPanel()

    expect(byId(wrapper, 'list-error').exists()).toBe(true)
  })

  // AC-4
  it('creates a minor and shows the login id the system generated', async () => {
    vi.mocked(api.createStudent).mockResolvedValue({ id: 's3', displayName: 'Pablo', loginId: 'pablo-9x2', status: 'ACTIVE' })
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'display-name').setValue(' Pablo ')
    await byId(wrapper, 'login-hint').setValue('pablo')
    await byId(wrapper, 'password').setValue('Correct-Horse-9')
    await byId(wrapper, 'create-form').trigger('submit')
    await flushPromises()

    expect(api.createStudent).toHaveBeenCalledWith({
      displayName: 'Pablo', loginHint: 'pablo', password: 'Correct-Horse-9',
    })
    expect(byId(wrapper, 'created-login-id').text()).toContain('pablo-9x2')
    expect(api.listStudents).toHaveBeenCalledTimes(2) // recarga la lista
  })

  it('masks the password typed for the minor', async () => {
    const { wrapper } = await mountPanel()
    expect(byId(wrapper, 'password').attributes('type')).toBe('password')
  })

  it('does not call the API with a short password or a blank name', async () => {
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'display-name').setValue('Pablo')
    await byId(wrapper, 'login-hint').setValue('pablo')
    await byId(wrapper, 'password').setValue('corta')
    await byId(wrapper, 'create-form').trigger('submit')
    await byId(wrapper, 'display-name').setValue('   ')
    await byId(wrapper, 'password').setValue('Correct-Horse-9')
    await byId(wrapper, 'create-form').trigger('submit')
    await flushPromises()

    expect(api.createStudent).not.toHaveBeenCalled()
  })

  it('shows a clear message when the name already exists (409)', async () => {
    vi.mocked(api.createStudent).mockRejectedValue(new ApiError(409))
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'display-name').setValue('Hugo')
    await byId(wrapper, 'login-hint').setValue('hugo')
    await byId(wrapper, 'password').setValue('Correct-Horse-9')
    await byId(wrapper, 'create-form').trigger('submit')
    await flushPromises()

    expect(byId(wrapper, 'create-error').exists()).toBe(true)
  })

  it('ignores a second submit while the first one is still in flight (double submit)', async () => {
    vi.mocked(api.createStudent).mockImplementation(() => new Promise(() => undefined))
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'display-name').setValue('Pablo')
    await byId(wrapper, 'login-hint').setValue('pablo')
    await byId(wrapper, 'password').setValue('Correct-Horse-9')
    await byId(wrapper, 'create-form').trigger('submit')
    await byId(wrapper, 'create-form').trigger('submit')

    expect(api.createStudent).toHaveBeenCalledTimes(1)
  })

  // AC-7
  it('resets a minor\'s password from a dialog', async () => {
    vi.mocked(api.resetStudentPassword).mockResolvedValue(undefined)
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'reset-s1').trigger('click')
    await byId(wrapper, 'new-password').setValue('Otra-Clave-1234')
    await byId(wrapper, 'reset-confirm').trigger('click')
    await flushPromises()

    expect(api.resetStudentPassword).toHaveBeenCalledWith('s1', 'Otra-Clave-1234')
  })

  it('does not reset with a short password', async () => {
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'reset-s1').trigger('click')
    await byId(wrapper, 'new-password').setValue('corta')
    await byId(wrapper, 'reset-confirm').trigger('click')
    await flushPromises()

    expect(api.resetStudentPassword).not.toHaveBeenCalled()
  })

  it('offers "deactivate" for active minors and "reactivate" for deactivated ones', async () => {
    const { wrapper } = await mountPanel()

    expect(byId(wrapper, 'deactivate-s1').exists()).toBe(true)
    expect(byId(wrapper, 'reactivate-s1').exists()).toBe(false)
    expect(byId(wrapper, 'reactivate-s2').exists()).toBe(true)
    expect(byId(wrapper, 'deactivate-s2').exists()).toBe(false)
  })

  it('deactivates and reactivates, reloading the list each time', async () => {
    vi.mocked(api.deactivateStudent).mockResolvedValue(undefined)
    vi.mocked(api.reactivateStudent).mockResolvedValue(undefined)
    const { wrapper } = await mountPanel()

    await byId(wrapper, 'deactivate-s1').trigger('click')
    await flushPromises()
    await byId(wrapper, 'reactivate-s2').trigger('click')
    await flushPromises()

    expect(api.deactivateStudent).toHaveBeenCalledWith('s1')
    expect(api.reactivateStudent).toHaveBeenCalledWith('s2')
    expect(api.listStudents).toHaveBeenCalledTimes(3)
  })

  it('renders names as text, never as HTML (XSS)', async () => {
    vi.mocked(api.listStudents).mockResolvedValue([
      { id: 's9', displayName: '<img src=x onerror=alert(1)>', loginId: 'x-1', status: 'ACTIVE' },
    ])
    const { wrapper } = await mountPanel()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(byId(wrapper, 'student-s9').text()).toContain('<img src=x onerror=alert(1)>')
  })
})

// F08 AC-1: cada alumno, activo o dado de baja, lleva a su panel de actividad
describe('StudentsPanel: acceso al panel de actividad', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([hugo, lola])
  })

  it('offers the activity panel of every student, also of a deactivated one', async () => {
    const { wrapper } = await mountPanel()

    expect(byId(wrapper, 'student-activity-s1').attributes('href')).toBe('/alumnos/s1')
    expect(byId(wrapper, 'student-activity-s2').attributes('href')).toBe('/alumnos/s2')
  })

  it('shows no access when the adult has no students', async () => {
    vi.mocked(api.listStudents).mockResolvedValue([])
    const { wrapper } = await mountPanel()

    expect(wrapper.find('[data-testid^="student-activity-"]').exists()).toBe(false)
  })
})
