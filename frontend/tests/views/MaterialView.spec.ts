import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { listMaterial: vi.fn(), uploadMaterial: vi.fn(), deleteMaterial: vi.fn(), me: vi.fn(), logout: vi.fn() },
  }
})
import { ApiError, api } from '@/api/client'
import MaterialView from '@/views/MaterialView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const base = { chunkCount: 0, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true, sizeBytes: 2048 }
const ready = { ...base, id: 'd1', name: 'apuntes.pdf', mediaType: 'PDF', status: 'READY', chunkCount: 12 }
const processing = { ...base, id: 'd2', name: 'resumen.docx', mediaType: 'DOCX', status: 'PROCESSING' }
const failed = { ...base, id: 'd3', name: 'foto.pdf', mediaType: 'PDF', status: 'ERROR', errorKind: 'NO_TEXT' }

const empty = { own: [], minors: [] }
const list = (own: object[] = [], minors: object[] = []) => ({ own, minors })

function fileInput(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) {
  return byId(wrapper, 'file-input')
}

async function choose(wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper'], file: File) {
  const input = fileInput(wrapper)
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

const pdfFile = (size = 1024) => new File([new Uint8Array(size)], 'nuevo.pdf', { type: 'application/pdf' })

describe('MaterialView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue(list([ready, processing, failed]) as never)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  // AC-16
  it('lists my documents with their name, type and a readable status', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-d1').text()).toContain('apuntes.pdf')
    expect(byId(wrapper, 'doc-status-d1').text()).toBe('Listo')
    expect(byId(wrapper, 'doc-status-d2').text()).toBe('Procesando')
    expect(byId(wrapper, 'doc-status-d3').text()).toBe('Error')
  })

  it('shows a clear reason when a document ended in error', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-error-d3').text().toLowerCase()).toContain('escaneado')
  })

  it('shows a generic reason for an unknown error kind instead of a raw code', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(list([{ ...failed, errorKind: 'ALGO_RARO_INTERNO' }]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-error-d3').text()).not.toContain('ALGO_RARO_INTERNO')
    expect(byId(wrapper, 'doc-error-d3').text().length).toBeGreaterThan(5)
  })

  it('shows an empty-state message when there are no documents', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(empty as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'no-documents').exists()).toBe(true)
  })

  it('shows an error if the list cannot be loaded', async () => {
    vi.mocked(api.listMaterial).mockRejectedValue(new ApiError(500))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'list-error').exists()).toBe(true)
  })

  // AC-16 / PROJECT.md §8: nunca v-html con datos del usuario
  it('renders a hostile file name as plain text, never as HTML', async () => {
    const hostile = '<img src=x onerror=alert(1)>.txt'
    vi.mocked(api.listMaterial).mockResolvedValue(list([{ ...ready, name: hostile }]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-d1').text()).toContain(hostile)
    expect(wrapper.find('img').exists()).toBe(false)
  })

  // ---------- subida ----------

  it('uploads the chosen file and reloads the list', async () => {
    vi.mocked(api.uploadMaterial).mockResolvedValue({ ...base, id: 'd9', name: 'nuevo.pdf', mediaType: 'PDF', status: 'UPLOADED' } as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })
    const file = pdfFile()

    await choose(wrapper, file)

    expect(api.uploadMaterial).toHaveBeenCalledWith(file)
    expect(api.listMaterial).toHaveBeenCalledTimes(2)
  })

  it('accepts pdf, docx, txt and md by extension', async () => {
    vi.mocked(api.uploadMaterial).mockResolvedValue({} as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    for (const name of ['a.pdf', 'b.docx', 'c.txt', 'd.md', 'E.PDF']) {
      await choose(wrapper, new File(['x'], name))
    }

    expect(api.uploadMaterial).toHaveBeenCalledTimes(5)
  })

  it('rejects an unsupported type before calling the API, explaining which are allowed', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, new File(['x'], 'programa.exe'))

    expect(api.uploadMaterial).not.toHaveBeenCalled()
    expect(byId(wrapper, 'upload-error').text()).toContain('PDF')
  })

  it('rejects a file over 10 MB before calling the API', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, pdfFile(10 * 1024 * 1024 + 1))

    expect(api.uploadMaterial).not.toHaveBeenCalled()
    expect(byId(wrapper, 'upload-error').text()).toContain('10 MB')
  })

  it('rejects an empty file before calling the API', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, pdfFile(0))

    expect(api.uploadMaterial).not.toHaveBeenCalled()
    expect(byId(wrapper, 'upload-error').exists()).toBe(true)
  })

  it.each([
    ['MATERIAL_LIMIT_REACHED', 409, 'límite'],
    ['MATERIAL_DUPLICATE', 409, 'ya'],
    ['MATERIAL_TOO_LARGE', 413, '10 MB'],
    ['MATERIAL_TYPE_NOT_ALLOWED', 415, 'PDF'],
  ])('shows a clear message when the server answers %s', async (code, status, fragment) => {
    vi.mocked(api.uploadMaterial).mockRejectedValue(new ApiError(status, code))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, pdfFile())

    expect(byId(wrapper, 'upload-error').text()).toContain(fragment)
  })

  it('shows a generic message for an unexpected upload failure', async () => {
    vi.mocked(api.uploadMaterial).mockRejectedValue(new ApiError(500))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, pdfFile())

    expect(byId(wrapper, 'upload-error').exists()).toBe(true)
  })

  it('clears the previous upload error when the next upload succeeds', async () => {
    vi.mocked(api.uploadMaterial).mockRejectedValueOnce(new ApiError(413, 'MATERIAL_TOO_LARGE')).mockResolvedValueOnce({} as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await choose(wrapper, pdfFile())
    expect(byId(wrapper, 'upload-error').exists()).toBe(true)
    await choose(wrapper, pdfFile())

    expect(byId(wrapper, 'upload-error').exists()).toBe(false)
  })

  // ---------- borrado ----------

  it('asks for confirmation before deleting and only then calls the API', async () => {
    vi.mocked(api.deleteMaterial).mockResolvedValue(undefined as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d1').trigger('click')
    expect(api.deleteMaterial).not.toHaveBeenCalled()
    await byId(wrapper, 'confirm-delete-d1').trigger('click')
    await flushPromises()

    expect(api.deleteMaterial).toHaveBeenCalledWith('d1')
    expect(api.listMaterial).toHaveBeenCalledTimes(2)
  })

  it('does not delete when the confirmation is cancelled', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d1').trigger('click')
    await byId(wrapper, 'cancel-delete-d1').trigger('click')

    expect(api.deleteMaterial).not.toHaveBeenCalled()
    expect(byId(wrapper, 'confirm-delete-d1').exists()).toBe(false)
  })

  it('shows an error if the deletion fails and keeps the document listed', async () => {
    vi.mocked(api.deleteMaterial).mockRejectedValue(new ApiError(500))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d1').trigger('click')
    await byId(wrapper, 'confirm-delete-d1').trigger('click')
    await flushPromises()

    expect(byId(wrapper, 'delete-error').exists()).toBe(true)
    expect(byId(wrapper, 'doc-d1').exists()).toBe(true)
  })

  // ---------- estado en vivo ----------

  it('refreshes the list by itself while some document is still being processed', async () => {
    vi.useFakeTimers()
    vi.mocked(api.listMaterial)
      .mockResolvedValueOnce(list([processing]) as never)
      .mockResolvedValue(list([{ ...processing, status: 'READY', chunkCount: 5 }]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })
    expect(byId(wrapper, 'doc-status-d2').text()).toBe('Procesando')

    await vi.advanceTimersByTimeAsync(3100)
    await flushPromises()

    expect(byId(wrapper, 'doc-status-d2').text()).toBe('Listo')
  })

  it('stops polling once nothing is pending', async () => {
    vi.useFakeTimers()
    vi.mocked(api.listMaterial).mockResolvedValue(list([ready, failed]) as never)
    await mountApp(MaterialView, { user: adult })
    const calls = vi.mocked(api.listMaterial).mock.calls.length

    await vi.advanceTimersByTimeAsync(20000)

    expect(vi.mocked(api.listMaterial).mock.calls.length).toBe(calls)
  })

  it('stops polling when the user leaves the page', async () => {
    vi.useFakeTimers()
    vi.mocked(api.listMaterial).mockResolvedValue(list([processing]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })
    wrapper.unmount()
    const calls = vi.mocked(api.listMaterial).mock.calls.length

    await vi.advanceTimersByTimeAsync(20000)

    expect(vi.mocked(api.listMaterial).mock.calls.length).toBe(calls)
  })

  // ---------- tutor y menor ----------

  it('shows the tutor the documents of their minors in a read-only section, without delete buttons', async () => {
    const hugoDoc = { ...ready, id: 'h1', name: 'de-hugo.txt', ownedByMe: false }
    vi.mocked(api.listMaterial).mockResolvedValue(
      list([ready], [{ student: { id: 's1', displayName: 'Hugo' }, documents: [hugoDoc] }]) as never,
    )
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'minors-material').exists()).toBe(true)
    expect(byId(wrapper, 'minor-material-s1').text()).toContain('Hugo')
    expect(byId(wrapper, 'doc-h1').text()).toContain('de-hugo.txt')
    expect(byId(wrapper, 'delete-h1').exists()).toBe(false)
  })

  it('shows no minors section to an adult without minors', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'minors-material').exists()).toBe(false)
  })

  it('tells a minor that their tutor can see their documents, and shows no minors section', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: minor })

    expect(byId(wrapper, 'material-minor-notice').exists()).toBe(true)
    expect(byId(wrapper, 'minors-material').exists()).toBe(false)
  })

  it('does not show the minor notice to an adult', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'material-minor-notice').exists()).toBe(false)
  })
})
