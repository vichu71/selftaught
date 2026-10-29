import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: { startIntake: vi.fn(), createCourse: vi.fn(), listMaterial: vi.fn(), uploadMaterial: vi.fn(), me: vi.fn(), logout: vi.fn() },
  }
})
import { ApiError, api } from '@/api/client'
import NewCourseView from '@/views/NewCourseView.vue'
import { adult, byId, mountApp } from '../helpers'

type Mounted = Awaited<ReturnType<typeof mountApp>>

const doc = (id: string, name: string, status = 'READY', extra: object = {}) => ({
  id, name, mediaType: 'PDF', sizeBytes: 100, status, chunkCount: 1, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true, ...extra,
})
const list = (own: object[]) => ({ own, minors: [] }) as never
const file = (name = 'apuntes-mates.pdf') => new File([new Uint8Array(1024)], name, { type: 'application/pdf' })

async function toMaterialStep(m: Mounted) {
  await byId(m.wrapper, 'topic-input').setValue('Matemáticas de 1º de la ESO')
  await byId(m.wrapper, 'topic-next').trigger('click')
  await flushPromises()
}

async function chooseFile(m: Mounted, chosen: File) {
  const input = byId(m.wrapper, 'file-input')
  Object.defineProperty(input.element, 'files', { value: [chosen], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

// F18 AC-1 a AC-3, AC-10 (D1 a D3): el asistente deja subir los apuntes del curso y los marca solos al estar listos
describe('Nuevo curso: material del curso (F18)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue(list([doc('d1', 'apuntes.pdf')]))
    vi.mocked(api.uploadMaterial).mockResolvedValue(doc('n1', 'apuntes-mates.pdf', 'PROCESSING') as never)
  })
  afterEach(() => vi.useRealTimers())

  it('shows the drop zone «Añade los apuntes de este curso» only after answering yes', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)

    expect(byId(m.wrapper, 'wizard-dropzone').exists()).toBe(false)

    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    const zone = byId(m.wrapper, 'wizard-dropzone')
    expect(zone.exists()).toBe(true)
    expect(zone.text()).toContain('Añade los apuntes de este curso')
    expect(byId(m.wrapper, 'file-input').attributes('type')).toBe('file')
    expect(byId(m.wrapper, 'file-input').classes()).toContain('visually-hidden')

    await byId(m.wrapper, 'material-no').trigger('click')
    expect(byId(m.wrapper, 'wizard-dropzone').exists()).toBe(false)
  })

  it('uploads with the same upload as «Mi material» and selects the new document when it is ready', async () => {
    vi.useFakeTimers()
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()
    const chosen = file()
    vi.mocked(api.listMaterial)
      .mockResolvedValueOnce(list([doc('d1', 'apuntes.pdf'), doc('n1', 'apuntes-mates.pdf', 'PROCESSING')]))
      .mockResolvedValue(list([doc('d1', 'apuntes.pdf'), doc('n1', 'apuntes-mates.pdf', 'READY')]))

    await chooseFile(m, chosen)

    expect(api.uploadMaterial).toHaveBeenCalledWith(chosen)
    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).disabled).toBe(true)
    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).checked).toBe(false)

    await vi.advanceTimersByTimeAsync(3100)
    await flushPromises()

    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).disabled).toBe(false)
    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).checked).toBe(true)
    expect((byId(m.wrapper, 'doc-option-d1').element as HTMLInputElement).checked).toBe(false)
  })

  it('selects a document uploaded that is already ready at once', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()
    vi.mocked(api.uploadMaterial).mockResolvedValue(doc('n1', 'apuntes-mates.pdf') as never)
    vi.mocked(api.listMaterial).mockResolvedValue(list([doc('d1', 'apuntes.pdf'), doc('n1', 'apuntes-mates.pdf')]))

    await chooseFile(m, file())

    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).checked).toBe(true)
  })

  it('shows the same error as «Mi material» and selects nothing when the upload fails', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()
    vi.mocked(api.uploadMaterial).mockRejectedValue(new ApiError(409, 'MATERIAL_DUPLICATE'))

    await chooseFile(m, file())

    expect(byId(m.wrapper, 'upload-error').text()).toContain('Este documento ya está subido.')
    expect(m.wrapper.findAll('input[type="checkbox"]:checked')).toHaveLength(0)
  })

  it('does not select a document that ended with an error and says why', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()
    vi.mocked(api.uploadMaterial).mockResolvedValue(doc('n1', 'escaneado.pdf', 'ERROR', { errorKind: 'NO_TEXT' }) as never)
    vi.mocked(api.listMaterial).mockResolvedValue(list([doc('n1', 'escaneado.pdf', 'ERROR', { errorKind: 'NO_TEXT' })]))

    await chooseFile(m, file('escaneado.pdf'))

    expect((byId(m.wrapper, 'doc-option-n1').element as HTMLInputElement).checked).toBe(false)
    expect(byId(m.wrapper, 'wizard-doc-note').text()).toMatch(/texto|escane/i)
  })

  it('keeps offering the documents already in the library and asks for 1 to 5 when yes', async () => {
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'doc-option-d1').exists()).toBe(true)
    expect(m.wrapper.text()).toContain('o elige alguno que ya tengas')

    await byId(m.wrapper, 'material-next').trigger('click')
    await flushPromises()

    expect(byId(m.wrapper, 'docs-error').exists()).toBe(true)
    expect(api.startIntake).not.toHaveBeenCalled()
  })

  it('invites to upload here, and no longer sends to «Mi material», when there are no documents', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(list([]))
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    expect(m.wrapper.text()).toContain('Todavía no tienes documentos')
    expect(m.wrapper.find('a[href="/material"]').exists()).toBe(false)
    expect(byId(m.wrapper, 'wizard-dropzone').exists()).toBe(true)
  })

  it('shows document names as plain text, also hostile ones', async () => {
    vi.mocked(api.listMaterial).mockResolvedValue(list([doc('x1', '<img src=x onerror=alert(1)>.pdf')]))
    const m = await mountApp(NewCourseView, { user: adult })
    await toMaterialStep(m)
    await byId(m.wrapper, 'material-yes').trigger('click')
    await flushPromises()

    expect(m.wrapper.find('img').exists()).toBe(false)
    expect(m.wrapper.text()).toContain('<img src=x onerror=alert(1)>.pdf')
  })
})
