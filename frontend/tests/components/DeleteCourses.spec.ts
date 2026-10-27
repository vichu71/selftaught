import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listCourses: vi.fn(), deleteCourses: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CoursesPanel from '@/components/CoursesPanel.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const course = (id: string, title: string, status = 'READY') => ({
  id, title, status, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [],
})
const all = () => [course('c1', 'Inglés'), course('c2', 'Cocina básica'), course('c3', '<img src=x onerror=alert(1)>')]

// El diálogo se pinta fuera del componente (Reka lo teletransporta al body)
const inBody = (id: string) => document.body.querySelector<HTMLElement>('[data-testid="' + id + '"]')
const dialogText = () => (inBody('delete-courses-dialog')?.textContent ?? '').toLowerCase()
const clickBody = async (id: string) => {
  inBody(id)!.click()
  await flushPromises()
}
afterEach(() => { document.body.innerHTML = '' })

async function selecting(ids: string[], user = adult) {
  const m = await mountApp(CoursesPanel, { user })
  await byId(m.wrapper, 'courses-select-toggle').trigger('click')
  for (const id of ids) await byId(m.wrapper, `course-select-${id}`).setValue(true)
  return m
}

// AC-10, AC-11, AC-12: selección múltiple en la lista y diálogo de confirmación
describe('CoursesPanel: borrar cursos', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCourses).mockResolvedValue(all() as never)
    vi.mocked(api.deleteCourses).mockResolvedValue(undefined as never)
  })

  it('shows no checkboxes until the user switches to select mode', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-select-c1').exists()).toBe(false)
    expect(byId(m.wrapper, 'courses-delete-open').exists()).toBe(false)

    await byId(m.wrapper, 'courses-select-toggle').trigger('click')

    expect(byId(m.wrapper, 'course-select-c1').exists()).toBe(true)
    expect(byId(m.wrapper, 'course-select-c3').exists()).toBe(true)
  })

  it('does not offer select mode to a minor', async () => {
    const m = await mountApp(CoursesPanel, { user: minor })

    expect(byId(m.wrapper, 'courses-select-toggle').exists()).toBe(false)
  })

  it('does not offer select mode when there are no courses', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([])
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'courses-select-toggle').exists()).toBe(false)
  })

  it('counts the selected courses, keeps the delete button disabled with none and enables it with some', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })
    await byId(m.wrapper, 'courses-select-toggle').trigger('click')

    expect(byId(m.wrapper, 'courses-delete-open').attributes('disabled')).toBeDefined()

    await byId(m.wrapper, 'course-select-c1').setValue(true)
    await byId(m.wrapper, 'course-select-c2').setValue(true)

    expect(byId(m.wrapper, 'courses-selected-count').text()).toContain('2')
    expect(byId(m.wrapper, 'courses-delete-open').attributes('disabled')).toBeUndefined()
    expect(byId(m.wrapper, 'courses-delete-open').text()).toContain('2')
  })

  it('selects and deselects every course with «Seleccionar todos»', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })
    await byId(m.wrapper, 'courses-select-toggle').trigger('click')

    await byId(m.wrapper, 'courses-select-all').setValue(true)
    expect(byId(m.wrapper, 'courses-selected-count').text()).toContain('3')

    await byId(m.wrapper, 'courses-select-all').setValue(false)
    expect(byId(m.wrapper, 'courses-delete-open').attributes('disabled')).toBeDefined()
  })

  it('gives every checkbox an accessible name that includes the course title', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })
    await byId(m.wrapper, 'courses-select-toggle').trigger('click')

    const label = byId(m.wrapper, 'course-select-c1').attributes('aria-label') ?? m.wrapper.find('label[for]').text()
    expect(label).toContain('Inglés')
  })

  it('leaves select mode and clears the selection when it is switched off', async () => {
    const m = await selecting(['c1'])

    await byId(m.wrapper, 'courses-select-toggle').trigger('click')

    expect(byId(m.wrapper, 'course-select-c1').exists()).toBe(false)
    await byId(m.wrapper, 'courses-select-toggle').trigger('click')
    expect((byId(m.wrapper, 'course-select-c1').element as HTMLInputElement).checked).toBe(false)
  })

  // ---------- diálogo ----------

  it('opens a dialog that says how many courses, lists their titles as plain text and says what is lost and what is kept', async () => {
    const m = await selecting(['c1', 'c3'])

    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    const text = dialogText()
    expect(text).toContain('2')
    expect(text).toContain('inglés')
    expect(text).toContain('<img src=x onerror=alert(1)>')
    expect(inBody('delete-courses-dialog')!.querySelector('img')).toBeNull()
    expect(text).toContain('lecciones')
    expect(text).toContain('intentos')
    expect(text).toContain('conversaciones')
    expect(text).toContain('gasto')
    expect(text).toContain('copias')
    expect(text).toContain('material')
    expect(api.deleteCourses).not.toHaveBeenCalled()
  })

  it('offers to export each selected course before deleting it', async () => {
    const m = await selecting(['c1', 'c2'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    expect(inBody('delete-courses-export-c1')!.getAttribute('href')).toBe('/api/courses/c1/export')
    expect(inBody('delete-courses-export-c2')!.getAttribute('href')).toBe('/api/courses/c2/export')
  })

  it('does not delete anything when the dialog is cancelled', async () => {
    const m = await selecting(['c1'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-cancel')

    expect(api.deleteCourses).not.toHaveBeenCalled()
    expect(inBody('delete-courses-dialog')).toBeNull()
    expect(byId(m.wrapper, 'course-c1').exists()).toBe(true)
  })

  it('deletes the selected ids once confirmed, refreshes the list and leaves select mode', async () => {
    vi.mocked(api.listCourses).mockResolvedValueOnce(all() as never).mockResolvedValue([course('c2', 'Cocina básica')] as never)
    const m = await selecting(['c1', 'c3'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    expect(api.deleteCourses).toHaveBeenCalledTimes(1)
    expect(api.deleteCourses).toHaveBeenCalledWith(['c1', 'c3'])
    expect(api.listCourses).toHaveBeenCalledTimes(2)
    expect(byId(m.wrapper, 'course-c1').exists()).toBe(false)
    expect(byId(m.wrapper, 'course-c2').exists()).toBe(true)
    expect(byId(m.wrapper, 'course-select-c2').exists()).toBe(false)
  })

  it('says in plain words that a course is still being generated and never shows the backend code', async () => {
    vi.mocked(api.deleteCourses).mockRejectedValue(new ApiError(409, 'COURSE_BUSY'))
    const m = await selecting(['c1'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    const error = byId(m.wrapper, 'courses-delete-error')
    expect(error.exists()).toBe(true)
    expect(error.text().toLowerCase()).toContain('generando')
    expect(error.text()).not.toContain('COURSE_BUSY')
    expect(byId(m.wrapper, 'course-c1').exists()).toBe(true)
  })

  it('shows a generic error without codes when the delete fails', async () => {
    vi.mocked(api.deleteCourses).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const m = await selecting(['c1'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    expect(byId(m.wrapper, 'courses-delete-error').text()).not.toContain('INTERNAL')
    expect(byId(m.wrapper, 'courses-delete-error').text()).not.toContain('500')
  })

  it('treats a 404 as «already deleted»: no error, and the list is refreshed', async () => {
    vi.mocked(api.deleteCourses).mockRejectedValue(new ApiError(404))
    vi.mocked(api.listCourses).mockResolvedValueOnce(all() as never).mockResolvedValue([course('c2', 'Cocina básica')] as never)
    const m = await selecting(['c1'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    expect(byId(m.wrapper, 'courses-delete-error').exists()).toBe(false)
    expect(byId(m.wrapper, 'course-c1').exists()).toBe(false)
  })

  it('sends the request only once even if the confirm button is pressed twice', async () => {
    const m = await selecting(['c1'])
    await byId(m.wrapper, 'courses-delete-open').trigger('click')
    await flushPromises()

    inBody('delete-courses-confirm')!.click()
    inBody('delete-courses-confirm')?.click()
    await flushPromises()

    expect(api.deleteCourses).toHaveBeenCalledTimes(1)
  })
})
