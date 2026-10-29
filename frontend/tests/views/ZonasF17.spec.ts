import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listStudents: vi.fn(), listCourses: vi.fn(), logout: vi.fn(), me: vi.fn(), setLessonDone: vi.fn(), login: vi.fn() } }
})
import { api } from '@/api/client'
import LessonControls from '@/components/LessonControls.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import RedeemView from '@/views/RedeemView.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'
import { mount } from '@vue/test-utils'
import { adult, byId, minor, mountApp } from '../helpers'

const sources = import.meta.glob('/src/views/*.vue', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

// F17 AC-8: las pantallas pasan a PageHeader y conservan lo suyo
describe('F17 · pantallas con cabecera de zona', () => {
  it.each([
    'HomeView', 'MaterialView', 'ResultsView', 'OwnerView', 'CourseStatusView', 'PieceView', 'NewCourseView', 'RedeemView',
    'LoginView', 'RegisterView', 'GuardianStudentView',
  ])('%s uses PageHeader', (name) => {
    const source = sources[`/src/views/${name}.vue`]
    expect(source, name).toBeDefined()
    expect(source).toMatch(/import PageHeader from ['"]@\/ui\/PageHeader\.vue['"]/)
    expect(source).toMatch(/<PageHeader\b/)
  })

  it('keeps v-html out of every view', () => {
    for (const [file, source] of Object.entries(sources)) expect(source, file).not.toMatch(/v-html/)
  })
})

describe('F17 · pantallas montadas', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listStudents).mockResolvedValue([])
    vi.mocked(api.listCourses).mockResolvedValue([])
  })

  it('draws the page header in Inicio, Tengo un código and Login, with their testids', async () => {
    const home = await mountApp(HomeView, { user: adult })
    expect(byId(home.wrapper, 'page-header').exists()).toBe(true)
    expect(byId(home.wrapper, 'welcome').text()).toContain('Ana')

    const redeem = await mountApp(RedeemView, { user: adult, route: '/codigo' })
    expect(byId(redeem.wrapper, 'page-header').exists()).toBe(true)
    expect(redeem.wrapper.find('h1').text()).toContain('Tengo un código')

    const login = await mountApp(LoginView, { user: null, route: '/login' })
    expect(byId(login.wrapper, 'page-header').exists()).toBe(true)
  })

  it('says the part of the day in Inicio, as plain text, with no extra request', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'home-daypart').text()).toMatch(/^(Buenos días|Buenas tardes|Buenas noches)$/)
    expect(api.listStudents).not.toHaveBeenCalled()
  })

  it('keeps the guardian notice of a minor and shows no money in Inicio', async () => {
    const { wrapper } = await mountApp(HomeView, { user: minor })

    expect(byId(wrapper, 'guardian-notice').exists()).toBe(true)
    expect(wrapper.text()).not.toMatch(/USD|€/)
  })
})

// F17 AC-10, ADR-97
describe('F17 · alegría medida', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.setLessonDone).mockResolvedValue(undefined as never)
  })

  it('celebrates when a lesson is marked as done, and not when it was already done or is unmarked', async () => {
    const wrapper = mount(LessonControls, { props: { courseId: 'c1', pieceId: 'p1', done: false }, global: { stubs: { RouterLink: true } } })
    expect(byId(wrapper, 'celebration').exists()).toBe(false)

    await byId(wrapper, 'mark-done').trigger('click')
    await flushPromises()
    expect(byId(wrapper, 'celebration').exists()).toBe(true)
    expect(byId(wrapper, 'celebration').text()).toContain('¡Hecho!')

    await byId(wrapper, 'mark-done').trigger('click')
    await flushPromises()
    expect(byId(wrapper, 'celebration').exists()).toBe(false)

    const already = mount(LessonControls, { props: { courseId: 'c1', pieceId: 'p1', done: true }, global: { stubs: { RouterLink: true } } })
    expect(byId(already, 'celebration').exists()).toBe(false)
  })

  it('does not celebrate a save that failed', async () => {
    vi.mocked(api.setLessonDone).mockRejectedValue(new Error('x'))
    const wrapper = mount(LessonControls, { props: { courseId: 'c1', pieceId: 'p1', done: false }, global: { stubs: { RouterLink: true } } })

    await byId(wrapper, 'mark-done').trigger('click')
    await flushPromises()

    expect(byId(wrapper, 'celebration').exists()).toBe(false)
  })

  it('blooms only at the final stage of a plant', () => {
    for (const stage of ['seed', 'sprout', 'tree'] as const) {
      expect(mount(PlantIllustration, { props: { stage } }).classes(), stage).not.toContain('plant--bloom')
    }
    expect(mount(PlantIllustration, { props: { stage: 'fruit' } }).classes()).toContain('plant--bloom')
  })
})
