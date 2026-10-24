import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { forgotPassword: vi.fn(), resetPassword: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import { byId, mountApp } from '../helpers'

// AC-3b
describe('ForgotPasswordView', () => {
  beforeEach(() => vi.resetAllMocks())

  it('asks for the email and always shows the same confirmation', async () => {
    vi.mocked(api.forgotPassword).mockResolvedValue(undefined)
    const { wrapper } = await mountApp(ForgotPasswordView, { route: '/forgot-password' })

    await byId(wrapper, 'email').setValue('ana@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.forgotPassword).toHaveBeenCalledWith('ana@example.com')
    expect(byId(wrapper, 'sent').exists()).toBe(true)
  })

  it('does not call the API with an invalid email', async () => {
    const { wrapper } = await mountApp(ForgotPasswordView, { route: '/forgot-password' })

    await byId(wrapper, 'email').setValue('no-es-correo')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.forgotPassword).not.toHaveBeenCalled()
  })
})

describe('ResetPasswordView', () => {
  beforeEach(() => vi.resetAllMocks())

  async function submit(wrapper: ReturnType<typeof import('@vue/test-utils').mount>, a: string, b: string) {
    await byId(wrapper, 'new-password').setValue(a)
    await byId(wrapper, 'confirm-password').setValue(b)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
  }

  it('sends the token from the link with the new password and offers the login', async () => {
    vi.mocked(api.resetPassword).mockResolvedValue(undefined)
    const { wrapper } = await mountApp(ResetPasswordView, { route: '/reset-password?token=tok123' })

    await submit(wrapper, 'Nueva-Clave-77x', 'Nueva-Clave-77x')

    expect(api.resetPassword).toHaveBeenCalledWith('tok123', 'Nueva-Clave-77x')
    expect(byId(wrapper, 'done').exists()).toBe(true)
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })

  it('does not call the API when the passwords differ or are too short', async () => {
    const { wrapper } = await mountApp(ResetPasswordView, { route: '/reset-password?token=tok123' })

    await submit(wrapper, 'Nueva-Clave-77x', 'Otra-Distinta-88y')
    await submit(wrapper, 'corta', 'corta')

    expect(api.resetPassword).not.toHaveBeenCalled()
  })

  it('shows an error for an expired or used link', async () => {
    vi.mocked(api.resetPassword).mockRejectedValue(new ApiError(400))
    const { wrapper } = await mountApp(ResetPasswordView, { route: '/reset-password?token=caducado' })

    await submit(wrapper, 'Nueva-Clave-77x', 'Nueva-Clave-77x')

    expect(byId(wrapper, 'error').exists()).toBe(true)
    expect(byId(wrapper, 'done').exists()).toBe(false)
  })

  it('shows an error without calling the API when the link has no token', async () => {
    const { wrapper } = await mountApp(ResetPasswordView, { route: '/reset-password' })

    await submit(wrapper, 'Nueva-Clave-77x', 'Nueva-Clave-77x')

    expect(api.resetPassword).not.toHaveBeenCalled()
    expect(byId(wrapper, 'error').exists()).toBe(true)
  })
})
