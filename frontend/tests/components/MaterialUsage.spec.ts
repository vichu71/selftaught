import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { listMaterial: vi.fn(), uploadMaterial: vi.fn(), deleteMaterial: vi.fn(), getMaterialUsage: vi.fn(), me: vi.fn(), logout: vi.fn() },
  }
})
import { api } from '@/api/client'
import MaterialView from '@/views/MaterialView.vue'
import { adult, byId, mountApp } from '../helpers'

const doc = (id: string, name: string, extra: object = {}) => ({
  id, name, mediaType: 'PDF', sizeBytes: 2048, status: 'READY', chunkCount: 3, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true, ...extra,
})
const list = (own: object[], minors: object[] = []) => ({ own, minors }) as never

// F18 AC-6 y AC-7 (D5, D6): «Usado en» en cada documento y aviso al borrar uno que se usa
describe('Mi material · usado en (F18)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue(list([doc('d1', 'apuntes.pdf'), doc('d2', 'suelto.pdf')]))
    vi.mocked(api.getMaterialUsage).mockResolvedValue({
      d1: [{ id: 'c2', title: 'Segundo curso' }, { id: 'c1', title: 'Primer curso' }],
    } as never)
  })

  it('shows, for each document, the courses that use it as chips that lead to the course, newest first', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    const used = byId(wrapper, 'doc-usage-d1')
    expect(used.text()).toContain('Usado en')
    const chips = used.findAll('a')
    expect(chips.map((c) => c.text())).toEqual(['Segundo curso', 'Primer curso'])
    expect(byId(wrapper, 'doc-usage-chip-d1-c2').attributes('href')).toBe('/cursos/c2')
    expect(byId(wrapper, 'doc-usage-chip-d1-c1').classes()).toContain('ui-tag')
  })

  it('says «Sin usar todavía» for a document no course uses', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-unused-d2').text()).toContain('Sin usar todavía')
    expect(byId(wrapper, 'doc-usage-d2').exists()).toBe(false)
  })

  it('keeps the document list working, without the usage line, when the usage cannot be read', async () => {
    vi.mocked(api.getMaterialUsage).mockRejectedValue(new Error('boom'))

    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-d1').exists()).toBe(true)
    expect(byId(wrapper, 'doc-d2').exists()).toBe(true)
    expect(byId(wrapper, 'doc-usage-d1').exists()).toBe(false)
    expect(byId(wrapper, 'doc-unused-d1').exists()).toBe(false)
    expect(byId(wrapper, 'list-error').exists()).toBe(false)
  })

  it('warns before deleting a used document that the existing courses do not change, and still asks for confirmation', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d1').trigger('click')

    expect(byId(wrapper, 'delete-usage-note-d1').text()).toContain('2 cursos')
    expect(byId(wrapper, 'delete-usage-note-d1').text()).toContain('no cambian')
    expect(wrapper.text()).toContain('¿Borrar este documento?')
    expect(api.deleteMaterial).not.toHaveBeenCalled()

    vi.mocked(api.deleteMaterial).mockResolvedValue(undefined as never)
    await byId(wrapper, 'confirm-delete-d1').trigger('click')
    await flushPromises()
    expect(api.deleteMaterial).toHaveBeenCalledWith('d1')
  })

  it('does not add a usage note when deleting an unused document', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d2').trigger('click')

    expect(byId(wrapper, 'delete-usage-note-d2').exists()).toBe(false)
  })

  it('shows course titles as plain text, also hostile ones', async () => {
    vi.mocked(api.getMaterialUsage).mockResolvedValue({ d1: [{ id: 'c9', title: '<img src=x onerror=alert(1)>' }] } as never)

    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-usage-d1').find('img').exists()).toBe(false)
    expect(byId(wrapper, 'doc-usage-d1').text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('does not show usage for the read-only material of the minors', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(
      list([doc('d1', 'apuntes.pdf')], [{ student: { id: 's1', displayName: 'Hugo' }, documents: [doc('m1', 'de-hugo.pdf', { ownedByMe: false })] }]),
    )
    vi.mocked(api.getMaterialUsage).mockResolvedValue({ m1: [{ id: 'c1', title: 'Curso de Hugo' }] } as never)

    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-usage-m1').exists()).toBe(false)
    expect(byId(wrapper, 'doc-unused-m1').exists()).toBe(false)
  })
})
