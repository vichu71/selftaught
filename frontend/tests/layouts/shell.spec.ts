import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { logout: vi.fn(), me: vi.fn() } }
})
import { api } from '@/api/client'
import App from '@/App.vue'
import Breadcrumb from '@/layouts/Breadcrumb.vue'
import NavBar from '@/layouts/NavBar.vue'
import { adult, byId, minor, mountApp } from '../helpers'

// F10 AC-3: marco común y marco de acceso
describe('App shell', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('wraps the authenticated pages in the shell with the navigation', async () => {
    const { wrapper } = await mountApp(App, { user: adult, route: '/codigo' })

    expect(byId(wrapper, 'shell-app').exists()).toBe(true)
    expect(byId(wrapper, 'nav-bar').exists()).toBe(true)
    expect(byId(wrapper, 'shell-auth').exists()).toBe(false)
  })

  it('uses the reduced shell, without navigation, on the access pages', async () => {
    for (const route of ['/login', '/register', '/forgot-password', '/reset-password', '/verify-email']) {
      const { wrapper } = await mountApp(App, { user: null, route })

      expect(byId(wrapper, 'shell-auth').exists(), route).toBe(true)
      expect(byId(wrapper, 'nav-bar').exists(), route).toBe(false)
    }
  })

  it('offers a link to skip the navigation and lands on the main content', async () => {
    const { wrapper } = await mountApp(App, { user: adult, route: '/codigo' })

    const skip = byId(wrapper, 'skip-link')
    expect(skip.attributes('href')).toBe('#contenido')
    expect(wrapper.find('main#contenido').exists()).toBe(true)
  })
})

describe('NavBar', () => {
  const open = (route: string, user = adult) => mountApp(NavBar, { user, route })

  it('links to the map, the material and the results', async () => {
    const { wrapper } = await open('/material')

    expect(byId(wrapper, 'nav-map').attributes('href')).toBe('/')
    expect(byId(wrapper, 'nav-material').attributes('href')).toBe('/material')
    expect(byId(wrapper, 'nav-results').attributes('href')).toBe('/resultados')
  })

  it('marks the current section visually and with aria-current, and only that one', async () => {
    const { wrapper } = await open('/material')

    expect(byId(wrapper, 'nav-material').attributes('aria-current')).toBe('page')
    expect(byId(wrapper, 'nav-map').attributes('aria-current')).toBeUndefined()
    expect(byId(wrapper, 'nav-results').attributes('aria-current')).toBeUndefined()
    expect(byId(wrapper, 'nav-material').classes()).toContain('is-active')
  })

  it('keeps the map section active inside a course', async () => {
    const { wrapper } = await open('/cursos/c1/piezas/l3')

    expect(byId(wrapper, 'nav-map').attributes('aria-current')).toBe('page')
  })

  it('names the navigation landmark and shows who is signed in', async () => {
    const { wrapper } = await open('/')

    expect(wrapper.find('nav').attributes('aria-label')).toBeTruthy()
    expect(byId(wrapper, 'nav-user').text()).toContain('Ana')
  })

  it('keeps the logout button and its behaviour', async () => {
    vi.mocked(api.logout).mockResolvedValue(undefined)
    const { wrapper, router, auth } = await open('/')

    await byId(wrapper, 'logout').trigger('click')
    await flushPromises()

    expect(api.logout).toHaveBeenCalled()
    expect(auth.user).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('has the same sections for a minor (the backend decides what they can do)', async () => {
    const { wrapper } = await open('/', minor)

    expect(byId(wrapper, 'nav-results').exists()).toBe(true)
    expect(byId(wrapper, 'nav-user').text()).toContain('Hugo')
  })
})

describe('Breadcrumb', () => {
  it('links the previous steps and marks the last one as the current page', () => {
    const w = mount(Breadcrumb, {
      props: { items: [{ label: 'Inicio', to: '/' }, { label: 'Historia antigua', to: '/cursos/c1' }, { label: 'El Nilo y su gente' }] },
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })

    expect(w.find('nav').attributes('aria-label')).toBeTruthy()
    const links = w.findAll('a')
    expect(links.map((l) => l.attributes('href'))).toEqual(['/', '/cursos/c1'])
    expect(w.find('[aria-current="page"]').text()).toBe('El Nilo y su gente')
    expect(w.find('[aria-current="page"]').element.tagName).not.toBe('A')
  })

  it('shows item labels as text', () => {
    const w = mount(Breadcrumb, {
      props: { items: [{ label: '<img src=x onerror=alert(1)>' }] },
      global: { stubs: { RouterLink: true } },
    })

    expect(w.find('img').exists()).toBe(false)
  })
})
