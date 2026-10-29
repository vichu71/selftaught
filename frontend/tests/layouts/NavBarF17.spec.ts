import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { me: vi.fn(), logout: vi.fn() } }
})
import AppShell from '@/layouts/AppShell.vue'
import NavBar from '@/layouts/NavBar.vue'
import { adult, byId, minor, mountApp, ownerUser } from '../helpers'

// F17 AC-3, AC-4, AC-6, AC-7: barra con burbujas de color por zona, saludo como texto y marco con su zona
describe('NavBar · colour', () => {
  beforeEach(() => vi.resetAllMocks())

  it.each([['nav-map', 'map'], ['nav-material', 'material'], ['nav-results', 'results'], ['nav-owner', 'owner']])('gives %s its own zone and an icon bubble', async (id, zone) => {
    const { wrapper } = await mountApp(NavBar, { user: ownerUser, route: '/' })

    const link = byId(wrapper, id)
    expect(link.attributes('data-zone')).toBe(zone)
    expect(link.find('.nav-bubble').exists()).toBe(true)
    expect(link.find('.nav-bubble svg').exists()).toBe(true)
  })

  it('keeps the current section marked with aria-current and only that one', async () => {
    const { wrapper } = await mountApp(NavBar, { user: adult, route: '/resultados' })

    expect(byId(wrapper, 'nav-results').attributes('aria-current')).toBe('page')
    expect(byId(wrapper, 'nav-results').classes()).toContain('is-active')
    for (const id of ['nav-map', 'nav-material']) expect(byId(wrapper, id).attributes('aria-current')).toBeUndefined()
  })

  it('greets with the first name in a chip, as text', async () => {
    const { wrapper } = await mountApp(NavBar, { user: { ...adult, displayName: 'Victor Huecas' }, route: '/' })

    expect(byId(wrapper, 'nav-user').text()).toContain('¡Hola, Victor!')
    expect(byId(wrapper, 'nav-user').text()).not.toContain('Huecas')
  })

  it('shows hostile and very long names as plain text without breaking the bar', async () => {
    const hostile = await mountApp(NavBar, { user: { ...adult, displayName: '<img src=x onerror=alert(1)> Pérez' }, route: '/' })
    expect(hostile.wrapper.find('[data-testid="nav-user"] img').exists()).toBe(false)
    expect(byId(hostile.wrapper, 'nav-user').text()).toContain('<img')

    const long = await mountApp(NavBar, { user: { ...adult, displayName: 'x'.repeat(80) }, route: '/' })
    expect(byId(long.wrapper, 'nav-user').text().length).toBeLessThan(40)
  })

  it('greets without a name when the display name is empty', async () => {
    const { wrapper } = await mountApp(NavBar, { user: { ...adult, displayName: '' }, route: '/' })

    expect(byId(wrapper, 'nav-user').text()).toContain('¡Hola!')
  })

  it('keeps the rest and logout buttons and the mascot, and offers no owner link to a minor', async () => {
    const { wrapper } = await mountApp(NavBar, { user: minor, route: '/' })

    for (const id of ['rest', 'logout', 'nav-mascot']) expect(byId(wrapper, id).exists(), id).toBe(true)
    expect(byId(wrapper, 'nav-owner').exists()).toBe(false)
  })

  it('shows no money, cap or amount in the bar', async () => {
    const { wrapper } = await mountApp(NavBar, { user: minor, route: '/' })

    expect(wrapper.text()).not.toMatch(/USD|€|\$/)
  })
})

describe('AppShell · zone', () => {
  beforeEach(() => vi.resetAllMocks())

  it.each([['/', 'map'], ['/material', 'material'], ['/resultados', 'results'], ['/propietario', 'owner'], ['/codigo', 'map']])('puts data-zone on %s', async (route, zone) => {
    const { wrapper } = await mountApp(AppShell, { user: ownerUser, route })

    expect(byId(wrapper, 'shell-app').attributes('data-zone')).toBe(zone)
  })
})
