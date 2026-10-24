import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { register: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import RegisterView from '@/views/RegisterView.vue'
import { byId, mountApp } from '../helpers'

async function fill(wrapper: ReturnType<typeof import('@vue/test-utils').mount>, v: Record<string, string>) {
  await byId(wrapper, 'display-name').setValue(v.displayName)
  await byId(wrapper, 'email').setValue(v.email)
  await byId(wrapper, 'password').setValue(v.password)
}

describe('RegisterView', () => {
  beforeEach(() => vi.resetAllMocks())

  // AC-1 / AC-1b
  it('registers an adult and tells them to check their email', async () => {
    vi.mocked(api.register).mockResolvedValue(undefined)
    const { wrapper } = await mountApp(RegisterView, { route: '/register' })

    await fill(wrapper, { displayName: ' Ana ', email: 'ana@example.com', password: 'Correct-Horse-9' })
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.register).toHaveBeenCalledWith({
      email: 'ana@example.com', password: 'Correct-Horse-9', displayName: 'Ana',
    })
    expect(byId(wrapper, 'check-email').exists()).toBe(true)
    expect(byId(wrapper, 'check-email').text()).toContain('ana@example.com')
  })

  it('masks the password field', async () => {
    const { wrapper } = await mountApp(RegisterView, { route: '/register' })
    expect(byId(wrapper, 'password').attributes('type')).toBe('password')
  })

  // La validación del frontend es solo comodidad (la que cuenta es la del backend)
  it.each([
    ['invalid email', { displayName: 'Ana', email: 'no-es-correo', password: 'Correct-Horse-9' }],
    ['short password', { displayName: 'Ana', email: 'ana@example.com', password: 'corta' }],
    ['blank name', { displayName: '   ', email: 'ana@example.com', password: 'Correct-Horse-9' }],
  ])('does not call the API with %s and shows a field error', async (_name, values) => {
    const { wrapper } = await mountApp(RegisterView, { route: '/register' })

    await fill(wrapper, values)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.register).not.toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid$="-error"]').length).toBeGreaterThan(0)
  })

  it('shows a message when the server rejects the data', async () => {
    vi.mocked(api.register).mockRejectedValue(new ApiError(400))
    const { wrapper } = await mountApp(RegisterView, { route: '/register' })

    await fill(wrapper, { displayName: 'Ana', email: 'ana@example.com', password: 'Correct-Horse-9' })
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(byId(wrapper, 'error').exists()).toBe(true)
    expect(byId(wrapper, 'check-email').exists()).toBe(false)
  })

  it('ignores a second submit while the first one is still in flight', async () => {
    vi.mocked(api.register).mockImplementation(() => new Promise(() => undefined))
    const { wrapper } = await mountApp(RegisterView, { route: '/register' })

    await fill(wrapper, { displayName: 'Ana', email: 'ana@example.com', password: 'Correct-Horse-9' })
    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')

    expect(api.register).toHaveBeenCalledTimes(1)
  })
})
