import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { login: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import LoginView from '@/views/LoginView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

async function fill(wrapper: ReturnType<typeof import('@vue/test-utils').mount>, identifier: string, password: string) {
  await byId(wrapper, 'identifier').setValue(identifier)
  await byId(wrapper, 'password').setValue(password)
}

describe('LoginView', () => {
  beforeEach(() => vi.resetAllMocks())

  // AC-2
  it('has an identifier field, a masked password field and a submit button', async () => {
    const { wrapper } = await mountApp(LoginView, { route: '/login' })

    expect(byId(wrapper, 'identifier').exists()).toBe(true)
    expect(byId(wrapper, 'password').attributes('type')).toBe('password')
    expect(byId(wrapper, 'submit').exists()).toBe(true)
  })

  it('logs in an adult with their email and goes to the home', async () => {
    vi.mocked(api.login).mockResolvedValue(adult)
    const { wrapper, router } = await mountApp(LoginView, { route: '/login' })

    await fill(wrapper, ' ana@example.com ', 'Correct-Horse-9')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.login).toHaveBeenCalledWith('ana@example.com', 'Correct-Horse-9')
    expect(router.currentRoute.value.path).toBe('/')
  })

  // AC-6: el menor entra con su identificador, el mismo formulario
  it('lets a minor log in with their login id', async () => {
    vi.mocked(api.login).mockResolvedValue(minor)
    const { wrapper, router } = await mountApp(LoginView, { route: '/login' })

    await fill(wrapper, 'hugo', 'Correct-Horse-9')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.login).toHaveBeenCalledWith('hugo', 'Correct-Horse-9')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('shows a generic error that does not say whether the account exists', async () => {
    vi.mocked(api.login).mockRejectedValue(new ApiError(401))
    const { wrapper, router } = await mountApp(LoginView, { route: '/login' })

    await fill(wrapper, 'ana@example.com', 'mala-clave-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const error = byId(wrapper, 'error')
    expect(error.exists()).toBe(true)
    expect(error.text()).not.toMatch(/no existe|inexistente|not found/i)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  // AC-3
  it('explains the temporary lock differently from a wrong password', async () => {
    vi.mocked(api.login).mockRejectedValueOnce(new ApiError(401)).mockRejectedValueOnce(new ApiError(429))
    const { wrapper } = await mountApp(LoginView, { route: '/login' })

    await fill(wrapper, 'ana@example.com', 'mala-clave-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const wrong = byId(wrapper, 'error').text()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(byId(wrapper, 'error').text()).not.toBe(wrong)
  })

  it('does not call the API with empty fields', async () => {
    const { wrapper } = await mountApp(LoginView, { route: '/login' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.login).not.toHaveBeenCalled()
  })

  it('ignores a second submit while the first one is still in flight', async () => {
    let finish: (u: typeof adult) => void = () => undefined
    vi.mocked(api.login).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    const { wrapper } = await mountApp(LoginView, { route: '/login' })

    await fill(wrapper, 'ana@example.com', 'Correct-Horse-9')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')

    expect(api.login).toHaveBeenCalledTimes(1)
    expect(byId(wrapper, 'submit').attributes('disabled')).toBeDefined()
    finish(adult)
  })

  it('links to registration and to password recovery', async () => {
    const { wrapper } = await mountApp(LoginView, { route: '/login' })

    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))
    expect(hrefs).toContain('/register')
    expect(hrefs).toContain('/forgot-password')
  })
})
