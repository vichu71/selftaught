import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      ownerAi: vi.fn(), ownerPrices: vi.fn(), ownerAccounts: vi.fn(), ownerUsage: vi.fn(), ownerHealth: vi.fn(), ownerUsers: vi.fn(),
      ownerDeactivate: vi.fn(), ownerReactivate: vi.fn(), ownerRevoke: vi.fn(), ownerSetLimit: vi.fn(), ownerClearLimit: vi.fn(),
      me: vi.fn(), logout: vi.fn(),
    },
  }
})
import { ApiError, api } from '@/api/client'
import OwnerView from '@/views/OwnerView.vue'
import { byId, mountApp, ownerUser } from '../helpers'

const HOSTILE = '<img src=x onerror=alert(1)>'

const ai = (over: Record<string, unknown> = {}) => ({
  range: 30, todayUsd: 0.42, monthUsd: 6.1, rangeUsd: 6.1, projection: { usd: 14.8, reliable: true }, globalLimit: { limitUsd: 10, percent: 61 },
  daily: [{ day: '2026-10-25', usd: 1, calls: 3 }, { day: '2026-10-26', usd: 2, calls: 5 }, { day: '2026-10-27', usd: 0.42, calls: 2 }],
  byModel: [{ provider: 'anthropic', model: 'claude-haiku-5-5', usd: 3.9, calls: 10, errorPercent: 2.1, blockedPercent: 0.4, avgMs: 3100, p95Ms: 9800, inputTokens: 1000, outputTokens: 500 }],
  byProvider: [{ provider: 'anthropic', usd: 3.9, calls: 10 }], byTask: [{ task: 'LESSON', usd: 3.9, calls: 10 }],
  byUser: [{ userId: 'u-ana', kind: 'ADULT', label: 'Ana', usd: 3.9 }],
  totals: { calls: 10, errorPercent: 2.1, blockedPercent: 0.4, inputTokens: 1000, outputTokens: 500, avgMs: 3100 }, costPerCourse: 0.6, costPerActiveUser: 1.2,
  benchmark: { calls: 1, usd: 2 }, ...over,
})
const prices = () => ({
  text: [
    { provider: 'anthropic', model: 'claude-haiku-5-5', inputPerMillion: 0.1, outputPerMillion: 0.5, verifiedOn: '2026-10-25', stale: false },
    { provider: 'fake', model: 'fake-undated', inputPerMillion: 3, outputPerMillion: 4, verifiedOn: null, stale: true },
  ],
  image: [{ provider: 'openai', model: 'gpt-image-1-mini', perImage: 0.01, verifiedOn: '2026-10-27', stale: false }],
})
const row = (over: Record<string, unknown> = {}) => ({
  id: 'u-luis', kind: 'ADULT', status: 'ACTIVE', displayName: 'Luis', email: 'luis@example.com', createdAt: '2026-10-01T10:00:00Z', lastActivityAt: '2026-10-27T09:00:00Z',
  monthSpendUsd: 2.5, limitUsd: 5, limitIsCustom: false, courses: 2, ...over,
})
const users = (items: unknown[], extra: Record<string, unknown> = {}) => ({ items, total: items.length, page: 0, size: 25, ...extra })
const accounts = () => ({
  range: 30, signupsDaily: [{ day: '2026-10-27', count: 2 }], adults: 5, minors: 2, byStatus: { ACTIVE: 5, PENDING_VERIFICATION: 1, DEACTIVATED: 1 },
  active: { d1: 3, d7: 4, d30: 5 }, openSessions: 3, logins: [{ day: '2026-10-27', ok: 3, failed: 2, locked: 1 }],
})
const usage = () => ({
  range: 30, courses: { byStatus: { READY: 3, PAUSED: 1, FAILED: 1, PENDING_GENERATION: 1, GENERATING: 1 }, createdDaily: [{ day: '2026-10-27', count: 7 }] },
  pieces: { done: 40, failed: 2, pending: 3 }, study: { lessonsDone: 12, exerciseAttempts: 30, examAttempts: 4, examsPassed: 3 }, tutor: { questions: 20, blocked: 2, filtered: 1 },
  documents: { byStatus: { UPLOADED: 1, PROCESSING: 0, READY: 4, ERROR: 1 } }, illustrations: { byStatus: { PENDING: 1, DONE: 5, FAILED: 1, BLOCKED: 0, REMOVED: 0 } },
  sharing: { created: 6, redeemed: 3, revoked: 1 }, coursesDeleted: 2,
})
const health = () => ({
  courses: { pendingGeneration: 1, generating: 1 }, pieces: { pending: 3, failed: 2 }, documents: { pending: 1, failed: 1 }, illustrations: { pending: 1, failed: 1 },
  aiErrors: [{ provider: 'anthropic', errorKind: 'PROVIDER_ERROR', count: 4 }], contentBlocks24h: 3, databaseBytes: 52428800, tables: [{ name: 'ai_usage', rows: 1200 }, { name: 'audit_event', rows: 5000 }],
})

const inBody = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
const clickBody = async (id: string) => { inBody(id)!.click(); await flushPromises() }
afterEach(() => { document.body.innerHTML = '' })

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.ownerAi).mockResolvedValue(ai() as never)
  vi.mocked(api.ownerPrices).mockResolvedValue(prices() as never)
  vi.mocked(api.ownerAccounts).mockResolvedValue(accounts() as never)
  vi.mocked(api.ownerUsage).mockResolvedValue(usage() as never)
  vi.mocked(api.ownerHealth).mockResolvedValue(health() as never)
  vi.mocked(api.ownerUsers).mockResolvedValue(users([row()]) as never)
  for (const m of [api.ownerDeactivate, api.ownerReactivate, api.ownerRevoke, api.ownerSetLimit, api.ownerClearLimit]) vi.mocked(m).mockResolvedValue(undefined as never)
})

const open = () => mountApp(OwnerView, { user: ownerUser, route: '/propietario' })
const tab = async (m: Awaited<ReturnType<typeof open>>, id: string) => {
  await byId(m.wrapper, `owner-tab-${id}`).trigger('mousedown')
  await flushPromises()
}

// F14 AC-3, AC-11, AC-12: las cuatro pestañas del panel y sus estados
describe('OwnerView: gasto de IA', () => {
  it('opens on the spend tab for the last 30 days and asks only for what that tab needs', async () => {
    await open()

    expect(api.ownerAi).toHaveBeenCalledWith(30)
    expect(api.ownerPrices).toHaveBeenCalled()
    expect(api.ownerUsage).not.toHaveBeenCalled()
    expect(api.ownerHealth).not.toHaveBeenCalled()
  })

  it('shows today, the month, the projection and the global cap with their figures', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'owner-stat-today').text()).toContain('0,42')
    expect(byId(m.wrapper, 'owner-stat-month').text()).toContain('6,10')
    expect(byId(m.wrapper, 'owner-stat-projection').text()).toContain('14,80')
    expect(byId(m.wrapper, 'owner-stat-global').text()).toContain('61')
  })

  it('warns in words when the projection is not reliable yet', async () => {
    vi.mocked(api.ownerAi).mockResolvedValue(ai({ projection: { usd: 3, reliable: false } }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'owner-projection-note').text().toLowerCase()).toContain('poco fiable')
  })

  it.each([[61, 'dentro del tope'], [85, 'cerca del tope'], [100, 'tope superado']])('says the state of the global cap at %s %% in words', async (percent, words) => {
    vi.mocked(api.ownerAi).mockResolvedValue(ai({ globalLimit: { limitUsd: 10, percent } }) as never)
    const m = await open()

    expect(byId(m.wrapper, 'owner-stat-global').text().toLowerCase()).toContain(words)
  })

  it('draws the daily series with its equivalent table and the breakdown by model', async () => {
    const m = await open()

    expect(m.wrapper.findAll('[data-testid="chart-table"] tbody tr')).toHaveLength(3)
    expect(byId(m.wrapper, 'owner-by-model').text()).toContain('claude-haiku-5-5')
    expect(byId(m.wrapper, 'owner-by-model').text()).toContain('2,1') // % de errores del modelo
  })

  it('lists the prices with their verification date and flags old or undated ones in words', async () => {
    const m = await open()

    const text = byId(m.wrapper, 'owner-prices').text().toLowerCase()
    expect(text).toContain('claude-haiku-5-5')
    expect(text).toContain('25-10-2026')
    expect(text).toContain('sin fecha')
    expect(text).toContain('más de 90 días')
    expect(text).toContain('gpt-image-1-mini')
  })

  it('shows the benchmark spend on its own line', async () => {
    const m = await open()

    expect(byId(m.wrapper, 'owner-benchmark').text()).toContain('2,00')
  })

  it('switches the range, asks again for that range and marks the chosen one', async () => {
    const m = await open()

    await byId(m.wrapper, 'owner-range-90').trigger('click')
    await flushPromises()

    expect(api.ownerAi).toHaveBeenLastCalledWith(90)
    expect(byId(m.wrapper, 'owner-range-90').attributes('aria-pressed')).toBe('true')
    expect(byId(m.wrapper, 'owner-range-30').attributes('aria-pressed')).toBe('false')
  })

  it('refreshes only on demand, with the button, and never by itself', async () => {
    vi.useFakeTimers()
    try {
      const m = await open()
      const calls = vi.mocked(api.ownerAi).mock.calls.length

      vi.advanceTimersByTime(10 * 60 * 1000)
      await flushPromises()
      expect(api.ownerAi).toHaveBeenCalledTimes(calls)

      await byId(m.wrapper, 'owner-refresh').trigger('click')
      await flushPromises()
      expect(api.ownerAi).toHaveBeenCalledTimes(calls + 1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('shows an empty state when there is no spend and no NaN anywhere', async () => {
    vi.mocked(api.ownerAi).mockResolvedValue(ai({ daily: [], byModel: [], byProvider: [], byTask: [], byUser: [], rangeUsd: 0, todayUsd: 0, monthUsd: 0, projection: { usd: 0, reliable: false } }) as never)
    const m = await open()

    expect(m.wrapper.find('[data-testid="chart-empty"]').exists()).toBe(true)
    expect(m.wrapper.html()).not.toContain('NaN')
  })

  it('shows a loading message first and then a plain error without codes when the report fails', async () => {
    let fail: (e: unknown) => void = () => {}
    vi.mocked(api.ownerAi).mockReturnValue(new Promise((_, reject) => { fail = reject }) as never)
    const m = await open()
    expect(m.wrapper.find('[data-testid="owner-loading"]').exists()).toBe(true)

    fail(new ApiError(500, 'INTERNAL'))
    await flushPromises()

    const error = byId(m.wrapper, 'owner-error')
    expect(error.exists()).toBe(true)
    expect(error.text()).not.toMatch(/500|INTERNAL/)
  })

  it('explains in plain words that there were too many queries', async () => {
    vi.mocked(api.ownerAi).mockRejectedValue(new ApiError(429, 'OWNER_RATE_LIMIT'))
    const m = await open()

    expect(byId(m.wrapper, 'owner-error').text().toLowerCase()).toContain('demasiadas consultas')
    expect(byId(m.wrapper, 'owner-error').text()).not.toContain('OWNER_RATE_LIMIT')
  })

  it('renders hostile model, provider and user names as plain text', async () => {
    vi.mocked(api.ownerAi).mockResolvedValue(ai({ byModel: [{ provider: HOSTILE, model: HOSTILE, usd: 1, calls: 1, errorPercent: 0, blockedPercent: 0, avgMs: 1, p95Ms: 1, inputTokens: 1, outputTokens: 1 }], byUser: [{ userId: 'x', kind: 'ADULT', label: HOSTILE, usd: 1 }] }) as never)
    const m = await open()

    expect(m.wrapper.text()).toContain(HOSTILE)
    expect(m.wrapper.find('img').exists()).toBe(false)
  })
})

describe('OwnerView: usuarios y conexiones', () => {
  it('loads the account figures and the first page of users when its tab opens', async () => {
    const m = await open()

    await tab(m, 'users')

    expect(api.ownerAccounts).toHaveBeenCalledWith(30)
    expect(api.ownerUsers).toHaveBeenCalled()
    expect(byId(m.wrapper, 'owner-stat-active-7').text()).toContain('4')
    expect(byId(m.wrapper, 'owner-stat-sessions').text()).toContain('3')
    expect(byId(m.wrapper, 'owner-stat-minors').text()).toContain('2')
  })

  it('shows an adult with name and email and a minor with only the access id', async () => {
    vi.mocked(api.ownerUsers).mockResolvedValue(users([row(), row({ id: 'u-hugo', kind: 'MINOR', displayName: undefined, email: undefined, loginId: 'hugo-7f3a', tutorId: 'u-ana', limitUsd: 1 })]) as never)
    const m = await open()
    await tab(m, 'users')

    expect(byId(m.wrapper, 'owner-user-u-luis').text()).toContain('Luis')
    expect(byId(m.wrapper, 'owner-user-u-luis').text()).toContain('luis@example.com')
    const minor = byId(m.wrapper, 'owner-user-u-hugo').text()
    expect(minor).toContain('hugo-7f3a')
    expect(minor).not.toContain('@')
  })

  it('renders hostile names as plain text', async () => {
    vi.mocked(api.ownerUsers).mockResolvedValue(users([row({ displayName: HOSTILE })]) as never)
    const m = await open()
    await tab(m, 'users')

    expect(m.wrapper.text()).toContain(HOSTILE)
    expect(m.wrapper.find('img').exists()).toBe(false)
  })

  it('filters by kind and status and goes back to the first page', async () => {
    const m = await open()
    await tab(m, 'users')

    await byId(m.wrapper, 'owner-filter-kind').setValue('MINOR')
    await flushPromises()

    expect(api.ownerUsers).toHaveBeenLastCalledWith(expect.objectContaining({ kind: 'MINOR', page: 0 }))
    await byId(m.wrapper, 'owner-filter-status').setValue('DEACTIVATED')
    await flushPromises()
    expect(api.ownerUsers).toHaveBeenLastCalledWith(expect.objectContaining({ kind: 'MINOR', status: 'DEACTIVATED', page: 0 }))
  })

  it('pages through the users and says how many there are', async () => {
    vi.mocked(api.ownerUsers).mockResolvedValue(users([row()], { total: 60, size: 25 }) as never)
    const m = await open()
    await tab(m, 'users')
    expect(byId(m.wrapper, 'owner-users-total').text()).toContain('60')
    expect(byId(m.wrapper, 'owner-users-prev').attributes('disabled')).toBeDefined()

    await byId(m.wrapper, 'owner-users-next').trigger('click')
    await flushPromises()

    expect(api.ownerUsers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('shows a message when there are no users for the filter', async () => {
    vi.mocked(api.ownerUsers).mockResolvedValue(users([]) as never)
    const m = await open()
    await tab(m, 'users')

    expect(m.wrapper.find('[data-testid="owner-users-empty"]').exists()).toBe(true)
  })
})

describe('OwnerView: acciones sobre una cuenta', () => {
  const openUsers = async (items: unknown[] = [row()]) => {
    vi.mocked(api.ownerUsers).mockResolvedValue(users(items) as never)
    const m = await open()
    await tab(m, 'users')
    return m
  }

  it('asks for confirmation in a dialog (never the browser confirm) before deactivating, and does nothing if cancelled', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const m = await openUsers()

    await byId(m.wrapper, 'owner-user-u-luis-deactivate').trigger('click')
    await flushPromises()
    expect((inBody('owner-action-dialog')?.textContent ?? '').toLowerCase()).toContain('desactivar')
    await clickBody('owner-action-cancel')

    expect(api.ownerDeactivate).not.toHaveBeenCalled()
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('deactivates once confirmed and refreshes the list; a double click sends one request', async () => {
    const m = await openUsers()
    await byId(m.wrapper, 'owner-user-u-luis-deactivate').trigger('click')
    await flushPromises()

    inBody('owner-action-confirm')!.click()
    inBody('owner-action-confirm')?.click()
    await flushPromises()

    expect(api.ownerDeactivate).toHaveBeenCalledTimes(1)
    expect(api.ownerDeactivate).toHaveBeenCalledWith('u-luis')
    expect(vi.mocked(api.ownerUsers).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('offers «Reactivar» instead of «Desactivar» to a deactivated account, and reactivates it', async () => {
    const m = await openUsers([row({ status: 'DEACTIVATED' })])

    expect(byId(m.wrapper, 'owner-user-u-luis-deactivate').exists()).toBe(false)
    await byId(m.wrapper, 'owner-user-u-luis-reactivate').trigger('click')
    await flushPromises()
    await clickBody('owner-action-confirm')

    expect(api.ownerReactivate).toHaveBeenCalledWith('u-luis')
  })

  it('revokes the sessions after confirming', async () => {
    const m = await openUsers()

    await byId(m.wrapper, 'owner-user-u-luis-revoke').trigger('click')
    await flushPromises()
    await clickBody('owner-action-confirm')

    expect(api.ownerRevoke).toHaveBeenCalledWith('u-luis')
  })

  it('gives no way to deactivate or revoke the owner themselves, only to change their limit', async () => {
    const m = await openUsers([row({ id: 'u-ana', displayName: 'Ana', email: 'ana@example.com' })])

    expect(byId(m.wrapper, 'owner-user-u-ana-deactivate').exists()).toBe(false)
    expect(byId(m.wrapper, 'owner-user-u-ana-revoke').exists()).toBe(false)
    expect(byId(m.wrapper, 'owner-user-u-ana-limit').exists()).toBe(true)
  })

  it('sets a limit from a number field and sends the amount as a number', async () => {
    const m = await openUsers()
    await byId(m.wrapper, 'owner-user-u-luis-limit').trigger('click')
    await flushPromises()

    const input = inBody('owner-limit-input') as HTMLInputElement
    input.value = '7.5'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    await clickBody('owner-action-confirm')

    expect(api.ownerSetLimit).toHaveBeenCalledWith('u-luis', 7.5)
  })

  it.each(['', '-1', '500.01', 'abc', '1.234'])('does not let the owner confirm the invalid limit «%s»', async (value) => {
    const m = await openUsers()
    await byId(m.wrapper, 'owner-user-u-luis-limit').trigger('click')
    await flushPromises()

    const input = inBody('owner-limit-input') as HTMLInputElement
    input.value = value
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    expect((inBody('owner-action-confirm') as HTMLButtonElement).disabled).toBe(true)
    await clickBody('owner-action-confirm')
    expect(api.ownerSetLimit).not.toHaveBeenCalled()
  })

  it('warns that a limit below what the user already spent cuts them immediately', async () => {
    const m = await openUsers([row({ monthSpendUsd: 4 })])
    await byId(m.wrapper, 'owner-user-u-luis-limit').trigger('click')
    await flushPromises()

    const input = inBody('owner-limit-input') as HTMLInputElement
    input.value = '1'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    expect((inBody('owner-limit-warning')?.textContent ?? '').toLowerCase()).toContain('ya ha gastado')
  })

  it('offers to go back to the default limit only when there is a custom one', async () => {
    const m = await openUsers([row({ limitIsCustom: true, limitUsd: 9 }), row({ id: 'u-eva', displayName: 'Eva', email: 'eva@example.com' })])

    expect(byId(m.wrapper, 'owner-user-u-luis-limit-clear').exists()).toBe(true)
    expect(byId(m.wrapper, 'owner-user-u-eva-limit-clear').exists()).toBe(false)
    await byId(m.wrapper, 'owner-user-u-luis-limit-clear').trigger('click')
    await flushPromises()
    await clickBody('owner-action-confirm')

    expect(api.ownerClearLimit).toHaveBeenCalledWith('u-luis')
  })

  it.each([[400, 'CANNOT_TARGET_SELF'], [404, undefined], [500, 'INTERNAL']])('shows a plain message without the code when the server answers %s and keeps the dialog open', async (status, code) => {
    vi.mocked(api.ownerDeactivate).mockRejectedValue(new ApiError(status, code))
    const m = await openUsers()
    await byId(m.wrapper, 'owner-user-u-luis-deactivate').trigger('click')
    await flushPromises()

    await clickBody('owner-action-confirm')

    const error = inBody('owner-action-error')
    expect(error).not.toBeNull()
    expect(error!.textContent).not.toMatch(/CANNOT_TARGET_SELF|INTERNAL|\b(400|404|500)\b/)
    expect(inBody('owner-action-dialog')).not.toBeNull()
  })

  it('explains in plain words that there were too many actions', async () => {
    vi.mocked(api.ownerRevoke).mockRejectedValue(new ApiError(429, 'OWNER_RATE_LIMIT'))
    const m = await openUsers()
    await byId(m.wrapper, 'owner-user-u-luis-revoke').trigger('click')
    await flushPromises()

    await clickBody('owner-action-confirm')

    expect((inBody('owner-action-error')?.textContent ?? '').toLowerCase()).toContain('demasiadas')
  })

  it('shows the person as plain text inside the dialog', async () => {
    const m = await openUsers([row({ displayName: HOSTILE })])
    await byId(m.wrapper, 'owner-user-u-luis-deactivate').trigger('click')
    await flushPromises()

    expect(inBody('owner-action-dialog')!.textContent).toContain(HOSTILE)
    expect(inBody('owner-action-dialog')!.querySelector('img')).toBeNull()
  })
})

describe('OwnerView: uso y salud', () => {
  it('shows the usage figures: courses by status, study, tutor, material, illustrations and sharing', async () => {
    const m = await open()

    await tab(m, 'usage')

    expect(api.ownerUsage).toHaveBeenCalledWith(30)
    const text = byId(m.wrapper, 'owner-usage').text()
    for (const figure of ['12', '30', '20', '6', '40']) expect(text).toContain(figure)
    expect(byId(m.wrapper, 'owner-usage-deleted').text()).toContain('2')
  })

  it('shows the health figures with the database size in megabytes', async () => {
    const m = await open()

    await tab(m, 'health')

    expect(api.ownerHealth).toHaveBeenCalled()
    const text = byId(m.wrapper, 'owner-health').text()
    expect(text).toContain('50')
    expect(text.toLowerCase()).toContain('mb')
    expect(text).toContain('PROVIDER_ERROR')
    expect(text).toContain('ai_usage')
  })

  it('says in words, not only with a colour, that something is failing', async () => {
    const m = await open()

    await tab(m, 'health')

    expect(byId(m.wrapper, 'owner-health').text().toLowerCase()).toMatch(/fallid|atención|con fallos/)
  })

  it('changes the range of the usage tab and asks again', async () => {
    const m = await open()
    await tab(m, 'usage')

    await byId(m.wrapper, 'owner-range-7').trigger('click')
    await flushPromises()

    expect(api.ownerUsage).toHaveBeenLastCalledWith(7)
  })
})
