import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getCourse: vi.fn(), deleteCourse: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CourseStatusView from '@/views/CourseStatusView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const course = (status = 'READY') => ({
  id: 'c1', title: 'Inglés de 2º', status, createdAt: '2026-10-25T10:00:00Z', version: 1,
  sheet: { schemaVersion: 1, topic: 'Inglés de 2º', focus: 'x', startLevel: 'y', goal: 'z', topicsCount: 2, examPerTopic: true, documentIds: [] },
  documents: [], outline: [],
})

const inBody = (id: string) => document.body.querySelector<HTMLElement>('[data-testid="' + id + '"]')
const clickBody = async (id: string) => {
  inBody(id)!.click()
  await flushPromises()
}
afterEach(() => { document.body.innerHTML = '' })

// AC-10, AC-11: «Borrar curso» desde la pantalla del curso
describe('CourseStatusView: borrar el curso', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(course() as never)
    vi.mocked(api.deleteCourse).mockResolvedValue(undefined as never)
  })

  const open = (user = adult) => mountApp(CourseStatusView, { user, route: '/cursos/c1' })

  it('offers «Borrar curso» to an adult owner', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'course-delete-open').exists()).toBe(true)
  })

  it('does not offer it to a minor', async () => {
    const m = await open(minor)

    expect(byId(m.wrapper, 'course-delete-open').exists()).toBe(false)
  })

  it('opens a confirmation dialog (never the browser confirm) with the title as text, what is lost and the export link', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const m = await open()

    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    const text = (inBody('delete-courses-dialog')?.textContent ?? '').toLowerCase()
    expect(text).toContain('inglés de 2º')
    expect(text).toContain('para siempre')
    expect(text).toContain('gasto')
    expect(inBody('delete-courses-export-c1')!.getAttribute('href')).toBe('/api/courses/c1/export')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(api.deleteCourse).not.toHaveBeenCalled()
  })

  it('does nothing when cancelled', async () => {
    const m = await open()
    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-cancel')

    expect(api.deleteCourse).not.toHaveBeenCalled()
    expect(m.router.currentRoute.value.path).toBe('/cursos/c1')
  })

  it('deletes the course once confirmed and goes back to the list', async () => {
    const m = await open()
    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    expect(api.deleteCourse).toHaveBeenCalledWith('c1')
    expect(m.router.currentRoute.value.path).toBe('/')
  })

  it('treats a 404 (already deleted, e.g. a double click) as success and goes back to the list', async () => {
    vi.mocked(api.deleteCourse).mockRejectedValue(new ApiError(404))
    const m = await open()
    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    expect(byId(m.wrapper, 'course-delete-error').exists()).toBe(false)
    expect(m.router.currentRoute.value.path).toBe('/')
  })

  it('explains in plain words that the course is being generated and stays on the page', async () => {
    vi.mocked(api.deleteCourse).mockRejectedValue(new ApiError(409, 'COURSE_BUSY'))
    const m = await open()
    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    await clickBody('delete-courses-confirm')

    const error = byId(m.wrapper, 'course-delete-error')
    expect(error.text().toLowerCase()).toContain('generando')
    expect(error.text()).not.toContain('COURSE_BUSY')
    expect(m.router.currentRoute.value.path).toBe('/cursos/c1')
  })

  it('renders a hostile title as plain text inside the dialog', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ ...course(), title: '<img src=x onerror=alert(1)>' } as never)
    const m = await open()
    await byId(m.wrapper, 'course-delete-open').trigger('click')
    await flushPromises()

    expect(inBody('delete-courses-dialog')!.textContent).toContain('<img src=x onerror=alert(1)>')
    expect(inBody('delete-courses-dialog')!.querySelector('img')).toBeNull()
  })
})
