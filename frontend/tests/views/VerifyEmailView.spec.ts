import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { verifyEmail: vi.fn(), resendVerification: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import VerifyEmailView from '@/views/VerifyEmailView.vue'
import { byId, mountApp } from '../helpers'

// AC-1b
describe('VerifyEmailView', () => {
  beforeEach(() => vi.resetAllMocks())

  it('verifies the token from the link as soon as it opens and offers the login', async () => {
    vi.mocked(api.verifyEmail).mockResolvedValue(undefined)
    const { wrapper } = await mountApp(VerifyEmailView, { route: '/verify-email?token=abc123' })

    expect(api.verifyEmail).toHaveBeenCalledWith('abc123')
    expect(byId(wrapper, 'verified').exists()).toBe(true)
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })

  it('shows an error and a resend form when the link is invalid or expired', async () => {
    vi.mocked(api.verifyEmail).mockRejectedValue(new ApiError(400))
    const { wrapper } = await mountApp(VerifyEmailView, { route: '/verify-email?token=caducado' })

    expect(byId(wrapper, 'error').exists()).toBe(true)
    expect(byId(wrapper, 'verified').exists()).toBe(false)
    expect(byId(wrapper, 'resend-email').exists()).toBe(true)
  })

  it('resends the verification email to the address typed by the user', async () => {
    vi.mocked(api.verifyEmail).mockRejectedValue(new ApiError(400))
    vi.mocked(api.resendVerification).mockResolvedValue(undefined)
    const { wrapper } = await mountApp(VerifyEmailView, { route: '/verify-email?token=caducado' })

    await byId(wrapper, 'resend-email').setValue('ana@example.com')
    await byId(wrapper, 'resend-submit').trigger('click')
    await flushPromises()

    expect(api.resendVerification).toHaveBeenCalledWith('ana@example.com')
    expect(byId(wrapper, 'resent').exists()).toBe(true)
  })

  it('does not call the API when the link has no token', async () => {
    const { wrapper } = await mountApp(VerifyEmailView, { route: '/verify-email' })

    expect(api.verifyEmail).not.toHaveBeenCalled()
    expect(byId(wrapper, 'error').exists()).toBe(true)
  })
})
