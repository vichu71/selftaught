import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DropZone from '@/material/DropZone.vue'

const pdf = (name = 'apuntes.pdf') => new File([new Uint8Array(100)], name, { type: 'application/pdf' })

// F16 AC-5 (D5): la zona de subida es la etiqueta del <input type="file"> real; arrastrar y soltar solo entrega el primer fichero a la misma subida
describe('DropZone', () => {
  const make = (props: Record<string, unknown> = {}) => mount(DropZone, { props })
  const zone = (w: ReturnType<typeof make>) => w.get('[data-testid="drop-zone"]')
  const input = (w: ReturnType<typeof make>) => w.get('[data-testid="file-input"]')

  it('tells what to do, which types are accepted and how big, with a button to choose a file', () => {
    const w = make()

    const text = zone(w).text()
    expect(text).toContain('Arrastra un documento aquí')
    expect(text).toContain('PDF')
    expect(text).toContain('Word')
    expect(text).toContain('10 MB')
    expect(text).toContain('Elegir fichero')
    expect(zone(w).find('svg[aria-hidden="true"]').exists()).toBe(true)
  })

  it('keeps the real file input as the control: visually hidden but not removed, with its accepted types and a label that points to it', () => {
    const w = make()

    const el = input(w)
    expect(el.attributes('type')).toBe('file')
    expect(el.attributes('accept')).toBe('.pdf,.docx,.txt,.md')
    expect(el.classes()).toContain('visually-hidden')
    expect(el.attributes('style') ?? '').not.toContain('display: none')
    const label = w.get(`label[for="${el.attributes('id')}"]`)
    expect(label.text()).toContain('Elegir fichero')
  })

  it('emits the chosen file when the input changes', async () => {
    const w = make()
    const file = pdf()
    Object.defineProperty(input(w).element, 'files', { value: [file], configurable: true })

    await input(w).trigger('change')

    expect(w.emitted('choose')).toEqual([[file]])
  })

  it('emits the dropped file', async () => {
    const w = make()
    const file = pdf()

    await zone(w).trigger('drop', { dataTransfer: { files: [file] } })

    expect(w.emitted('choose')).toEqual([[file]])
  })

  it('takes only the first of several dropped files and says so in words', async () => {
    const w = make()
    const first = pdf('uno.pdf')

    await zone(w).trigger('drop', { dataTransfer: { files: [first, pdf('dos.pdf')] } })

    expect(w.emitted('choose')).toEqual([[first]])
    const note = w.get('[role="status"]')
    expect(note.text().toLowerCase()).toContain('primero')
  })

  it('does nothing when what is dropped is not a file', async () => {
    const w = make()

    await zone(w).trigger('drop', { dataTransfer: { files: [] } })

    expect(w.emitted('choose')).toBeUndefined()
  })

  it('accepts the drop (prevents the browser from opening the file) and shows it with a class while dragging over', async () => {
    const w = make()
    const over = new Event('dragover', { bubbles: true, cancelable: true })

    zone(w).element.dispatchEvent(over)
    await flushPromises()

    expect(over.defaultPrevented).toBe(true)
    expect(zone(w).classes()).toContain('is-over')
    await zone(w).trigger('dragleave')
    expect(zone(w).classes()).not.toContain('is-over')
  })

  it('stops showing the drag state after a drop', async () => {
    const w = make()

    await zone(w).trigger('dragover')
    await zone(w).trigger('drop', { dataTransfer: { files: [pdf()] } })

    expect(zone(w).classes()).not.toContain('is-over')
  })

  it('is switched off while uploading: it says so, disables the input and ignores drops', async () => {
    const w = make({ uploading: true })

    expect(zone(w).classes()).toContain('is-disabled')
    expect(zone(w).attributes('aria-disabled')).toBe('true')
    expect(zone(w).text()).toContain('Subiendo…')
    expect(input(w).attributes('disabled')).toBeDefined()
    await zone(w).trigger('drop', { dataTransfer: { files: [pdf()] } })
    expect(w.emitted('choose')).toBeUndefined()
  })

  it('can be disabled from outside too', async () => {
    const w = make({ disabled: true })

    expect(zone(w).classes()).toContain('is-disabled')
    expect(input(w).attributes('disabled')).toBeDefined()
  })

  it('writes no colour into the markup', () => {
    expect(make().html()).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(/)
  })
})
