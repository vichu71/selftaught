import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { redeemCode: vi.fn(), me: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import RedeemView from '@/views/RedeemView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const open = (user = adult) => mountApp(RedeemView, { user, route: '/codigo' })

async function submit(m: Awaited<ReturnType<typeof open>>, code = 'K7QM-4XAP-9TZE-2HDV') {
  await byId(m.wrapper, 'redeem-input').setValue(code)
  await byId(m.wrapper, 'redeem-send').trigger('click')
  await flushPromises()
}

// AC-10, AC-12, AC-13, AC-16: la pantalla «Tengo un código»
describe('Tengo un código', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.redeemCode).mockResolvedValue({ id: 'copia1', title: 'Historia' } as never)
  })

  it('sends the code the user typed and goes to the copy it creates', async () => {
    const m = await open()

    await submit(m)

    expect(api.redeemCode).toHaveBeenCalledWith('K7QM-4XAP-9TZE-2HDV')
    expect(m.router.currentRoute.value.path).toBe('/cursos/copia1')
  })

  it('sends nothing when the box is empty or only spaces', async () => {
    const m = await open()

    await submit(m, '   ')

    expect(api.redeemCode).not.toHaveBeenCalled()
    expect(byId(m.wrapper, 'redeem-error').exists()).toBe(true)
  })

  it('limits the length of what can be typed', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'redeem-input').attributes('maxlength')).toBe('40')
  })

  it('blocks a second send while waiting', async () => {
    let finish: (value: unknown) => void = () => undefined
    vi.mocked(api.redeemCode).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const m = await open()

    await byId(m.wrapper, 'redeem-input').setValue('K7QM-4XAP-9TZE-2HDV')
    await byId(m.wrapper, 'redeem-send').trigger('click')
    await byId(m.wrapper, 'redeem-send').trigger('click')
    finish({ id: 'copia1', title: 'x' })
    await flushPromises()

    expect(api.redeemCode).toHaveBeenCalledTimes(1)
  })

  it('says a code that does not work in one single plain way, without saying why', async () => {
    vi.mocked(api.redeemCode).mockRejectedValue(new ApiError(404, 'NOT_FOUND'))
    const m = await open()

    await submit(m)

    const text = byId(m.wrapper, 'redeem-error').text().toLowerCase()
    expect(text).toContain('código')
    expect(text).not.toContain('caduc')
    expect(text).not.toContain('revoc')
    expect(m.wrapper.text()).not.toContain('NOT_FOUND')
  })

  it('explains the rate limit with plain words', async () => {
    vi.mocked(api.redeemCode).mockRejectedValue(new ApiError(429, 'CODE_RATE_LIMITED'))
    const m = await open()

    await submit(m)

    expect(byId(m.wrapper, 'redeem-error').text().toLowerCase()).toContain('muchos')
    expect(m.wrapper.text()).not.toContain('CODE_RATE_LIMITED')
  })

  it('shows the agreed message to a minor whose copy trips the filter, without saying what tripped it', async () => {
    vi.mocked(api.redeemCode).mockRejectedValue(new ApiError(422, 'COPY_NOT_ALLOWED'))
    const m = await open(minor)

    await submit(m)

    expect(byId(m.wrapper, 'redeem-error').text()).toContain('Contenido inapropiado. Consulta con tu adulto.')
    expect(m.router.currentRoute.value.path).toBe('/codigo')
  })

  it('shows a generic message for any other failure and keeps what was typed', async () => {
    vi.mocked(api.redeemCode).mockRejectedValue(new ApiError(500))
    const m = await open()

    await submit(m, 'ABCD-EFGH-JKLM-NPQR')

    expect(byId(m.wrapper, 'redeem-error').text().toLowerCase()).toContain('no se pudo')
    expect((byId(m.wrapper, 'redeem-input').element as HTMLInputElement).value).toBe('ABCD-EFGH-JKLM-NPQR')
  })

  it('tells that the copy is the user own and does not change the original, and that no material or data travels', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'redeem-info').text().toLowerCase()
    expect(text).toContain('copia')
    expect(text).toContain('material')
  })

  it('works for a minor too', async () => {
    const m = await open(minor)

    await submit(m)

    expect(api.redeemCode).toHaveBeenCalled()
    expect(m.router.currentRoute.value.path).toBe('/cursos/copia1')
  })
})
