import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { listCourses: vi.fn(), getStudyProgress: vi.fn(), getCourse: vi.fn(), getStudy: vi.fn(), listStudents: vi.fn(), getPiece: vi.fn(), listShareCodes: vi.fn(), removeTopic: vi.fn(), getTutorThread: vi.fn(), logout: vi.fn(), me: vi.fn() },
  }
})
import { api } from '@/api/client'
import App from '@/App.vue'
import NavBar from '@/layouts/NavBar.vue'
import { useUiStore } from '@/stores/ui'
import { adult, byId, mountApp } from '../helpers'

// «Dejarlo por hoy»: vuelve al Inicio (las plantas y el progreso) y deja cerrado todo lo que estaba abierto
afterEach(() => {
  document.body.innerHTML = ''
})

describe('Dejarlo por hoy', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCourses).mockResolvedValue([{ id: 'c1', title: 'Historia antigua', status: 'READY', createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [] }] as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue([{ courseId: 'c1', percent: 38 }] as never)
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.getTutorThread).mockResolvedValue({ messages: [], remaining: 30 } as never)
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p1', kind: 'LESSON', status: 'DONE', title: 'El Nilo', markdown: '# Hola', sources: [] } as never)
  })

  it('is in the navigation bar of every page, with a name for assistive technology', async () => {
    const { wrapper } = await mountApp(NavBar, { user: adult, route: '/cursos/c1/piezas/p1' })

    const button = byId(wrapper, 'rest')
    expect(button.exists()).toBe(true)
    expect(button.text()).toContain('Dejarlo por hoy')
    expect(button.attributes('aria-label')).toBe('Dejarlo por hoy')
    expect(button.attributes('type')).toBe('button')
  })

  it('goes back to the main page from any other page', async () => {
    const { wrapper, router } = await mountApp(NavBar, { user: adult, route: '/cursos/c1/piezas/p1' })

    await byId(wrapper, 'rest').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('shows the main page with the progress of the courses, and closes the open tutor panel of the lesson', async () => {
    const { wrapper, router } = await mountApp(App, { user: adult, route: '/cursos/c1/piezas/p1' })
    await byId(wrapper, 'tutor-open').trigger('click')
    await flushPromises()
    expect(byId(wrapper, 'tutor-panel').isVisible()).toBe(true)

    await byId(wrapper, 'rest').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
    expect(byId(wrapper, 'tutor-panel').exists()).toBe(false)
    expect(byId(wrapper, 'course-percent-c1').find('[role="progressbar"]').attributes('aria-valuenow')).toBe('38')
  })

  it('also closes an open confirmation dialog of the edit screen', async () => {
    const piece = (id: string) => ({ id, status: 'DONE' })
    vi.mocked(api.getCourse).mockResolvedValue({
      id: 'c1', title: 'Historia', status: 'READY', version: 1, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [],
      outline: [
        { id: 't1', title: 'Uno', summary: 's', chapters: [{ id: 'ch1', title: 'A', objective: 'o', lesson: piece('l1'), exercises: piece('e1') }], exam: piece('x1') },
        { id: 't2', title: 'Dos', summary: 's', chapters: [{ id: 'ch2', title: 'B', objective: 'o', lesson: piece('l2'), exercises: piece('e2') }], exam: piece('x2') },
      ],
    } as never)
    const { wrapper, router } = await mountApp(App, { user: adult, route: '/cursos/c1/editar' })
    await byId(wrapper, 'edit-topic-remove-t1').trigger('click')
    await flushPromises()
    expect(document.body.querySelector('[role="dialog"]')).not.toBeNull()

    await byId(wrapper, 'rest').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
  })

  it('restarts the current page even when it already is the main page (the view is mounted again)', async () => {
    const { wrapper } = await mountApp(App, { user: adult, route: '/' })
    const ui = useUiStore()
    const before = ui.restKey
    const calls = vi.mocked(api.listCourses).mock.calls.length

    await byId(wrapper, 'rest').trigger('click')
    await flushPromises()

    expect(ui.restKey).toBe(before + 1)
    expect(vi.mocked(api.listCourses).mock.calls.length).toBe(calls + 1)
  })

  it('does not end the session', async () => {
    const { wrapper, auth } = await mountApp(NavBar, { user: adult, route: '/cursos/c1/piezas/p1' })

    await byId(wrapper, 'rest').trigger('click')
    await flushPromises()

    expect(api.logout).not.toHaveBeenCalled()
    expect(auth.user).not.toBeNull()
  })

  it('keeps the logout button next to it', async () => {
    const { wrapper } = await mountApp(NavBar, { user: adult, route: '/' })

    expect(byId(wrapper, 'logout').exists()).toBe(true)
    expect(byId(wrapper, 'rest').exists()).toBe(true)
  })
})
