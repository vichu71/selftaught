import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MaterialList from '@/components/MaterialList.vue'
import { reasonText } from '@/material/reasons'

const doc = (over: object = {}) => ({
  id: 'd1', name: 'apuntes.jpg', mediaType: 'IMAGE', sizeBytes: 409600, status: 'READY', chunkCount: 3, createdAt: '2026-10-29T10:00:00Z', ownedByMe: true, ...over,
})
const make = (documents: object[]) => mount(MaterialList, { props: { documents, readonly: false } })

// F19 AC-14: un documento que es una foto lleva su icono, su etiqueta «Foto» y la marca «Leído por IA»; los errores se dicen en palabras
describe('MaterialList con fotos', () => {
  it('draws an image icon, out of the reading order, and calls it a photo', () => {
    const w = make([doc()])

    const icon = w.get('[data-testid="doc-icon-d1"]')
    expect(icon.attributes('data-kind')).toBe('IMAGE')
    expect(icon.attributes('aria-hidden')).toBe('true')
    expect(w.get('[data-testid="doc-d1"]').text()).toContain('Foto · 400 KB')
  })

  it('marks a ready photo as read by AI, and nothing else', () => {
    const w = make([doc(), doc({ id: 'd2', mediaType: 'PDF', name: 'a.pdf' }), doc({ id: 'd3', status: 'PROCESSING' })])

    expect(w.get('[data-testid="doc-ai-read-d1"]').text()).toBe('Leído por IA')
    expect(w.find('[data-testid="doc-ai-read-d2"]').exists()).toBe(false)
    expect(w.find('[data-testid="doc-ai-read-d3"]').exists()).toBe(false)
  })

  it('keeps the status words of a photo that is being processed', () => {
    const w = make([doc({ status: 'PROCESSING' })])

    expect(w.get('[data-testid="doc-status-d1"]').text()).toBe('Procesando')
  })

  it.each([
    ['IMAGE_INVALID', 'foto'],
    ['IMAGE_TOO_LARGE', 'grande'],
    ['NO_TEXT', 'foto'],
    ['READ_FAILED', 'leer'],
    ['BUDGET', 'tope'],
  ])('says in words why a photo failed with %s and never shows the raw code', (kind, word) => {
    const w = make([doc({ status: 'ERROR', errorKind: kind })])

    const text = w.get('[data-testid="doc-error-d1"]').text()
    expect(text.toLowerCase()).toContain(word)
    expect(w.text()).not.toContain(kind)
  })

  it('invites to try again with more light when no text could be read in a photo', () => {
    expect(reasonText('NO_TEXT', 'IMAGE').toLowerCase()).toContain('luz')
  })

  it('keeps the F03 sentence for NO_TEXT in a scanned document', () => {
    expect(reasonText('NO_TEXT')).toBe('No contiene texto: los documentos escaneados no se admiten.')
    expect(reasonText('NO_TEXT', 'PDF')).toBe('No contiene texto: los documentos escaneados no se admiten.')
  })

  it('shows the agreed minor message for a blocked photo, with no category or reason', () => {
    const w = make([doc({ status: 'ERROR', errorKind: 'BLOCKED' })])

    const text = w.get('[data-testid="doc-error-d1"]').text()
    expect(text).toBe('Contenido inapropiado. Consulta con tu adulto.')
    expect(w.text()).not.toContain('BLOCKED')
  })

  it('renders a hostile photo name as plain text', () => {
    const w = make([doc({ name: '<img src=x onerror=alert(1)>.jpg' })])

    expect(w.text()).toContain('<img src=x onerror=alert(1)>.jpg')
    expect(w.find('img').exists()).toBe(false)
  })
})
