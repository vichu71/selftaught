import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MaterialList from '@/components/MaterialList.vue'

const doc = (over: object = {}) => ({
  id: 'd1', name: 'apuntes.pdf', mediaType: 'PDF', sizeBytes: 2048, status: 'READY', chunkCount: 12, createdAt: '2026-10-25T10:00:00Z', ownedByMe: true, ...over,
})

// F16 AC-6, AC-7 (D6, D8): los documentos como tarjetas, con icono por tipo y estado con icono; el borrado sigue pidiendo confirmación
describe('MaterialList', () => {
  const make = (documents: object[], readonly = false) => mount(MaterialList, { props: { documents, readonly } })

  it('shows each document as a card with its name, type and size as text', () => {
    const w = make([doc()])

    const card = w.get('[data-testid="doc-d1"]')
    expect(card.classes()).toContain('doc-card')
    expect(card.text()).toContain('apuntes.pdf')
    expect(card.text()).toContain('PDF · 2 KB')
  })

  it.each([['PDF', 'PDF'], ['DOCX', 'DOCX'], ['TEXT', 'TEXT']])('draws an icon for the %s type, out of the reading order', (type, kind) => {
    const w = make([doc({ mediaType: type })])

    const icon = w.get('[data-testid="doc-icon-d1"]')
    expect(icon.attributes('data-kind')).toBe(kind)
    expect(icon.attributes('aria-hidden')).toBe('true')
  })

  it('keeps the readable status texts and adds an icon to each state', () => {
    const w = make([doc(), doc({ id: 'd2', status: 'PROCESSING' }), doc({ id: 'd3', status: 'ERROR', errorKind: 'NO_TEXT' })])

    for (const [id, text] of [['d1', 'Listo'], ['d2', 'Procesando'], ['d3', 'Error']]) {
      const status = w.get(`[data-testid="doc-status-${id}"]`)
      expect(status.text()).toBe(text)
      expect(status.find('svg[aria-hidden="true"]').exists()).toBe(true)
    }
  })

  it('says why a document failed in one sentence, and never shows the raw code', () => {
    const w = make([doc({ id: 'd3', status: 'ERROR', errorKind: 'ENCRYPTED' }), doc({ id: 'd4', status: 'ERROR', errorKind: 'ALGO_RARO' })])

    expect(w.get('[data-testid="doc-error-d3"]').text().toLowerCase()).toContain('contraseña')
    expect(w.get('[data-testid="doc-error-d4"]').text()).toBe('No se pudo procesar el documento.')
    expect(w.text()).not.toContain('ALGO_RARO')
    expect(w.text()).not.toContain('ENCRYPTED')
  })

  it('asks for confirmation before removing, then emits remove with the id', async () => {
    const w = make([doc()])

    await w.get('[data-testid="delete-d1"]').trigger('click')
    expect(w.emitted('remove')).toBeUndefined()
    expect(w.text()).toContain('¿Borrar este documento?')
    await w.get('[data-testid="confirm-delete-d1"]').trigger('click')

    expect(w.emitted('remove')).toEqual([['d1']])
  })

  it('does not remove anything when the confirmation is cancelled', async () => {
    const w = make([doc()])

    await w.get('[data-testid="delete-d1"]').trigger('click')
    await w.get('[data-testid="cancel-delete-d1"]').trigger('click')

    expect(w.emitted('remove')).toBeUndefined()
    expect(w.find('[data-testid="delete-d1"]').exists()).toBe(true)
  })

  it('shows no delete button on a read-only list or on a document that is not mine', () => {
    expect(make([doc()], true).find('[data-testid="delete-d1"]').exists()).toBe(false)
    expect(make([doc({ ownedByMe: false })]).find('[data-testid="delete-d1"]').exists()).toBe(false)
  })

  it('renders a hostile file name as plain text', () => {
    const w = make([doc({ name: '<img src=x onerror=alert(1)>.pdf' })])

    expect(w.text()).toContain('<img src=x onerror=alert(1)>.pdf')
    expect(w.find('img').exists()).toBe(false)
  })
})
