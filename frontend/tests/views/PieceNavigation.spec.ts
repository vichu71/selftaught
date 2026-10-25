import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), me: vi.fn(), listCourses: vi.fn() } }
})
import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import CoursesPanel from '@/components/CoursesPanel.vue'
import { adult, byId, mountApp } from '../helpers'

// AC-14: la lectura de una pieza está protegida y el panel conoce los estados nuevos
describe('Piece navigation and course states', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getPiece).mockResolvedValue({ id: 'p1', kind: 'LESSON', status: 'PENDING', title: 'x', generatedByAi: true, sources: [] } as never)
  })

  it('redirects an anonymous visitor from a piece page to the login', async () => {
    setActivePinia(createPinia())
    vi.mocked(api.me).mockRejectedValue(new Error('401'))
    const router = createAppRouter(createMemoryHistory())

    await router.push('/cursos/c1/piezas/p1')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets a signed-in user reach a piece page with both ids in the route', async () => {
    const m = await mountApp(CoursesPanel, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(m.router.currentRoute.value.name).toBe('piece')
    expect(m.router.currentRoute.value.params).toMatchObject({ id: 'c1', pieceId: 'p1' })
  })

  it.each([
    ['PAUSED', 'pausa'],
    ['FAILED', 'no se pudo'],
    ['GENERATING', 'prepar'],
  ])('shows a readable label for the %s state in the course list', async (status, fragment) => {
    vi.mocked(api.listCourses).mockResolvedValue([{ id: 'c1', title: 'Inglés', status, createdAt: '', sheet: {}, documents: [] }] as never)
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-c1').text().toLowerCase()).toContain(fragment)
  })
})
