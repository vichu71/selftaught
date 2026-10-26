import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listShareCodes: vi.fn(), createShareCode: vi.fn(), revokeShareCode: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import ShareCodes from '@/components/ShareCodes.vue'
import { adult, byId, mountApp } from '../helpers'

const code = (over: object = {}) => ({ id: 'k1', createdAt: '2026-10-25T10:00:00Z', expiresAt: '2026-11-01T10:00:00Z', uses: 3, active: true, ...over })

async function open(status = 'READY') {
  const m = await mountApp({ components: { ShareCodes }, template: `<ShareCodes course-id="c1" status="${status}" />` }, { user: adult })
  await flushPromises()
  return m
}

// AC-8, AC-9, AC-16: crear, ver el estado y revocar los códigos de un curso
describe('ShareCodes', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listShareCodes).mockResolvedValue([code(), code({ id: 'k2', active: false, revokedAt: '2026-10-26T10:00:00Z', uses: 0 })] as never)
    vi.mocked(api.createShareCode).mockResolvedValue({ id: 'k3', code: 'K7QM-4XAP-9TZE-2HDV', expiresAt: '2026-11-01T10:00:00Z' } as never)
    vi.mocked(api.revokeShareCode).mockResolvedValue(undefined as never)
  })

  it('lists the codes of the course with their state and their uses, never the code', async () => {
    const m = await open()

    expect(api.listShareCodes).toHaveBeenCalledWith('c1')
    expect(byId(m.wrapper, 'share-code-k1').text().toLowerCase()).toContain('activo')
    expect(byId(m.wrapper, 'share-code-k1').text()).toContain('3')
    expect(byId(m.wrapper, 'share-code-k2').text().toLowerCase()).toContain('revocado')
  })

  it('creates a code with the default seven days and shows it once with a way to copy it', async () => {
    const m = await open()

    await byId(m.wrapper, 'share-create').trigger('click')
    await flushPromises()

    expect(api.createShareCode).toHaveBeenCalledWith('c1', 7)
    expect(byId(m.wrapper, 'share-new-code').text()).toContain('K7QM-4XAP-9TZE-2HDV')
    expect(byId(m.wrapper, 'share-copy').exists()).toBe(true)
    expect(byId(m.wrapper, 'share-once').text().toLowerCase()).toContain('una sola vez')
  })

  it('lets the user choose how long the code lasts, between one and ninety days', async () => {
    const m = await open()

    await byId(m.wrapper, 'share-days').setValue('30')
    await byId(m.wrapper, 'share-create').trigger('click')
    await flushPromises()

    expect(api.createShareCode).toHaveBeenCalledWith('c1', 30)
    expect(byId(m.wrapper, 'share-days').attributes('min')).toBe('1')
    expect(byId(m.wrapper, 'share-days').attributes('max')).toBe('90')
  })

  it('sends nothing for a number of days out of range', async () => {
    const m = await open()

    await byId(m.wrapper, 'share-days').setValue('120')
    await byId(m.wrapper, 'share-create').trigger('click')

    expect(api.createShareCode).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'share-error').exists()).toBe(true)
  })

  it('lets the user hide the new code once copied', async () => {
    const m = await open()
    await byId(m.wrapper, 'share-create').trigger('click')
    await flushPromises()

    await byId(m.wrapper, 'share-hide-code').trigger('click')

    expect(byId(m.wrapper, 'share-new-code').exists()).toBe(false)
  })

  it('revokes a code after asking and refreshes the list', async () => {
    const m = await open()

    await byId(m.wrapper, 'share-revoke-k1').trigger('click')
    expect(api.revokeShareCode).not.toHaveBeenCalled()
    await byId(m.wrapper, 'share-revoke-confirm').trigger('click')
    await flushPromises()

    expect(api.revokeShareCode).toHaveBeenCalledWith('c1', 'k1')
    expect(api.listShareCodes).toHaveBeenCalledTimes(2)
  })

  it('offers no revoke button for a code that is not active', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'share-revoke-k2').exists()).toBe(false)
  })

  it('does not offer to create codes while the course is not finished and says why', async () => {
    const m = await open('PAUSED')

    expect(byId(m.wrapper, 'share-create').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'share-not-ready').exists()).toBe(true)
  })

  it.each([
    ['SHARE_LIMIT', 409, 'cinco'],
    ['COURSE_NOT_READY', 409, 'terminado'],
  ])('explains the %s error in plain words', async (codeName, status, fragment) => {
    vi.mocked(api.createShareCode).mockRejectedValue(new ApiError(status, codeName))
    const m = await open()

    await byId(m.wrapper, 'share-create').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'share-error').text().toLowerCase()).toContain(fragment)
    expect(m.wrapper.text()).not.toContain(codeName)
  })

  it('shows an empty message when there are no codes and an error if the list cannot be loaded', async () => {
    vi.mocked(api.listShareCodes).mockResolvedValue([] as never)
    const empty = await open()
    expect(byId(empty.wrapper, 'share-empty').exists()).toBe(true)

    vi.mocked(api.listShareCodes).mockRejectedValue(new ApiError(500))
    const failed = await open()
    expect(byId(failed.wrapper, 'share-error').exists()).toBe(true)
  })
})
