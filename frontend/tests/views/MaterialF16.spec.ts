import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { listMaterial: vi.fn(), uploadMaterial: vi.fn(), deleteMaterial: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import MaterialView from '@/views/MaterialView.vue'
import { adult, byId, minor, mountApp } from '../helpers'

const base = { chunkCount: 0, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true, sizeBytes: 2048 }
const ready = { ...base, id: 'd1', name: 'apuntes.pdf', mediaType: 'PDF', status: 'READY', chunkCount: 12 }
const list = (own: object[] = [], minors: object[] = []) => ({ own, minors })
const file = (name = 'nuevo.pdf', size = 1024) => new File([new Uint8Array(size)], name, { type: 'application/pdf' })

// F16 AC-5 a AC-9 (D5 a D8): zona de subida, documentos como tarjetas, vacíos amables y material de los menores en solo lectura
describe('Mi material (F16)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue(list([ready]) as never)
    vi.mocked(api.uploadMaterial).mockResolvedValue(undefined as never)
  })
  afterEach(() => vi.useRealTimers())

  // ---------- la zona de subida (AC-5) ----------

  it('shows the drop zone with its real file input inside, instead of the bare browser input', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    const zone = byId(wrapper, 'drop-zone')
    expect(zone.exists()).toBe(true)
    expect(zone.text()).toContain('Arrastra un documento aquí')
    expect(zone.text()).toContain('10 MB')
    expect(byId(wrapper, 'file-input').attributes('type')).toBe('file')
    expect(byId(wrapper, 'file-input').classes()).toContain('visually-hidden')
  })

  it('uploads a file chosen with the input, as before', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })
    const input = byId(wrapper, 'file-input')
    const chosen = file()
    Object.defineProperty(input.element, 'files', { value: [chosen], configurable: true })

    await input.trigger('change')
    await flushPromises()

    expect(api.uploadMaterial).toHaveBeenCalledWith(chosen)
    expect(vi.mocked(api.listMaterial).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('uploads a file dropped on the zone through the same upload', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })
    const dropped = file('arrastrado.pdf')

    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [dropped] } })
    await flushPromises()

    expect(api.uploadMaterial).toHaveBeenCalledWith(dropped)
  })

  it('gives a dropped file the same checks and the same messages as a chosen one', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [new File(['x'], 'raro.exe', { type: 'application/x-msdownload' })] } })
    await flushPromises()
    expect(byId(wrapper, 'upload-error').text()).toContain('PDF')
    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [file('enorme.pdf', 11 * 1024 * 1024)] } })
    await flushPromises()
    expect(byId(wrapper, 'upload-error').text()).toContain('10 MB')
    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [new File([], 'vacio.pdf', { type: 'application/pdf' })] } })
    await flushPromises()
    expect(byId(wrapper, 'upload-error').text().toLowerCase()).toContain('vacío')
    expect(api.uploadMaterial).not.toHaveBeenCalled()
  })

  it('says «Subiendo…» and switches the zone off while a file is being uploaded', async () => {
    let finish: () => void = () => {}
    vi.mocked(api.uploadMaterial).mockReturnValue(new Promise<void>((resolve) => { finish = resolve }) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [file()] } })
    await flushPromises()
    expect(byId(wrapper, 'drop-zone').classes()).toContain('is-disabled')
    expect(byId(wrapper, 'uploading').text()).toContain('Subiendo')
    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [file('otro.pdf')] } })
    expect(api.uploadMaterial).toHaveBeenCalledTimes(1)

    finish()
    await flushPromises()
    expect(byId(wrapper, 'drop-zone').classes()).not.toContain('is-disabled')
  })

  it('shows a failed upload in words, never the code of the server', async () => {
    vi.mocked(api.uploadMaterial).mockRejectedValue(new ApiError(409, 'MATERIAL_DUPLICATE'))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'drop-zone').trigger('drop', { dataTransfer: { files: [file()] } })
    await flushPromises()

    expect(byId(wrapper, 'upload-error').text()).toContain('ya está subido')
    expect(byId(wrapper, 'upload-error').text()).not.toContain('MATERIAL_DUPLICATE')
  })

  // ---------- los documentos como tarjetas (AC-6) ----------

  it('lists the documents as cards with an icon, keeping the ids and the readable status', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-d1').classes()).toContain('doc-card')
    expect(byId(wrapper, 'doc-icon-d1').attributes('data-kind')).toBe('PDF')
    expect(byId(wrapper, 'doc-status-d1').text()).toBe('Listo')
  })

  it('still asks for confirmation before deleting and then deletes', async () => {
    vi.mocked(api.deleteMaterial).mockResolvedValue(undefined as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    await byId(wrapper, 'delete-d1').trigger('click')
    expect(api.deleteMaterial).not.toHaveBeenCalled()
    await byId(wrapper, 'confirm-delete-d1').trigger('click')
    await flushPromises()

    expect(api.deleteMaterial).toHaveBeenCalledWith('d1')
  })

  // ---------- vacíos y errores (AC-8) ----------

  it('shows a friendly note with the seed when there are no documents', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(list() as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    const note = byId(wrapper, 'no-documents')
    expect(note.classes()).toContain('empty-note')
    expect(note.text()).toContain('Todavía no has subido ningún documento')
    expect(note.find('svg').exists()).toBe(true)
  })

  it('says in words that the list could not be loaded, without codes', async () => {
    vi.mocked(api.listMaterial).mockRejectedValue(new ApiError(500, 'INTERNAL'))
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'list-error').text()).toContain('No se pudo cargar la lista')
    expect(byId(wrapper, 'list-error').text()).not.toMatch(/500|INTERNAL/)
  })

  // ---------- material de los menores (AC-7) ----------

  it('shows the minors material with the same cards in read-only, with the minor name as title and no delete button', async () => {
    const minorDoc = { ...base, id: 'm1', name: 'tarea.docx', mediaType: 'DOCX', status: 'READY', ownedByMe: false }
    vi.mocked(api.listMaterial).mockResolvedValue(list([ready], [{ student: { id: 's1', displayName: 'Hugo', loginId: 'hugo', status: 'ACTIVE' }, documents: [minorDoc] }]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    const block = byId(wrapper, 'minor-material-s1')
    expect(block.text()).toContain('Hugo')
    expect(block.find('.doc-card').exists()).toBe(true)
    expect(block.find('[data-testid="delete-m1"]').exists()).toBe(false)
    expect(byId(wrapper, 'doc-icon-m1').attributes('data-kind')).toBe('DOCX')
  })

  it('keeps the notice to a minor that the tutor can see their documents, in the same place, and shows no minors section', async () => {
    const { wrapper } = await mountApp(MaterialView, { user: minor })

    expect(byId(wrapper, 'material-minor-notice').text()).toBe('Tu adulto tutor puede ver los documentos que subes.')
    expect(byId(wrapper, 'minors-material').exists()).toBe(false)
    expect(byId(wrapper, 'drop-zone').text()).toContain('y tu tutor')
  })

  // ---------- texto plano (AC-9) ----------

  it('renders hostile and very long file names as plain text', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(list([{ ...ready, name: '<img src=x onerror=alert(1)>' + 'x'.repeat(200) + '.pdf' }]) as never)
    const { wrapper } = await mountApp(MaterialView, { user: adult })

    expect(byId(wrapper, 'doc-d1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
