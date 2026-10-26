import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listCourses: vi.fn(), getStudyProgress: vi.fn(), getCourse: vi.fn(), getStudy: vi.fn(), listStudents: vi.fn(), logout: vi.fn(), me: vi.fn() } }
})
import { api } from '@/api/client'
import App from '@/App.vue'
import CoursesPanel from '@/components/CoursesPanel.vue'
import { adult, byId, mountApp } from '../helpers'

const course = (id: string, title: string, status = 'READY') => ({ id, title, status, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [] })

// F11 AC-3: cada curso del Inicio es una planta
describe('Inicio · plantas', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([
      course('c0', 'Recién empezado'), course('c1', 'Con algo'), course('c2', 'Casi'), course('c3', 'Terminado'), course('c4', 'Preparándose', 'GENERATING'),
    ] as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue([
      { courseId: 'c0', percent: 0 }, { courseId: 'c1', percent: 38 }, { courseId: 'c2', percent: 45 }, { courseId: 'c3', percent: 100 }, { courseId: 'c4' },
    ] as never)
  })

  it('draws each course as a plant in the stage of its percentage', async () => {
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(byId(wrapper, 'plant-c0').attributes('data-stage')).toBe('seed')
    expect(byId(wrapper, 'plant-c1').attributes('data-stage')).toBe('sprout')
    expect(byId(wrapper, 'plant-c2').attributes('data-stage')).toBe('tree')
    expect(byId(wrapper, 'plant-c3').attributes('data-stage')).toBe('fruit')
  })

  it('says the stage in words next to each plant', async () => {
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(byId(wrapper, 'plant-stage-c0').text()).toBe('Semilla')
    expect(byId(wrapper, 'plant-stage-c1').text()).toBe('Brote')
    expect(byId(wrapper, 'plant-stage-c2').text()).toBe('Árbol')
    expect(byId(wrapper, 'plant-stage-c3').text()).toBe('Árbol con frutos')
  })

  it('a course without percentage is a seed that says it is being prepared, with no invented progress', async () => {
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(byId(wrapper, 'plant-c4').attributes('data-stage')).toBe('seed')
    expect(byId(wrapper, 'plant-stage-c4').text()).toBe('Preparándose')
    expect(byId(wrapper, 'course-percent-c4').exists()).toBe(false)
  })

  it('keeps the link, the status, the progress bar and the percentage of every course', async () => {
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(byId(wrapper, 'course-link-c1').attributes('href')).toBe('/cursos/c1')
    expect(byId(wrapper, 'course-percent-c1').find('[role="progressbar"]').attributes('aria-valuenow')).toBe('38')
    expect(byId(wrapper, 'new-course-link').attributes('href')).toBe('/nuevo-curso')
  })

  it('the plant is named for assistive technology with the course and its stage', async () => {
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(byId(wrapper, 'plant-c1').attributes('aria-label')).toContain('Con algo')
    expect(byId(wrapper, 'plant-c1').attributes('aria-label')).toContain('Brote')
  })

  it('shows a hostile course title as text only', async () => {
    vi.mocked(api.listCourses).mockResolvedValue([course('c1', '<img src=x onerror=alert(1)>')] as never)
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(byId(wrapper, 'course-link-c1').text()).toBe('<img src=x onerror=alert(1)>')
  })

  it('stays usable with 20 courses (the grid grows down, not wider)', async () => {
    vi.mocked(api.listCourses).mockResolvedValue(Array.from({ length: 20 }, (_, i) => course(`k${i}`, `Curso ${i}`)) as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue(Array.from({ length: 20 }, (_, i) => ({ courseId: `k${i}`, percent: i * 5 })) as never)
    const { wrapper } = await mountApp(CoursesPanel, { user: adult })

    expect(wrapper.findAll('[data-testid^="plant-k"]').length).toBeGreaterThanOrEqual(20)
  })
})

// F11 AC-2, AC-11: el paisaje vive en el marco
describe('Marco · paisaje', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('draws the landscape behind the authenticated pages and behind the access pages, as decoration', async () => {
    const inApp = await mountApp(App, { user: adult, route: '/codigo' })
    expect(byId(inApp.wrapper, 'shell-app').find('[data-testid="world-landscape"]').attributes('aria-hidden')).toBe('true')

    const inAuth = await mountApp(App, { user: null, route: '/login' })
    expect(byId(inAuth.wrapper, 'shell-auth').find('[data-testid="world-landscape"]').attributes('aria-hidden')).toBe('true')
  })
})
