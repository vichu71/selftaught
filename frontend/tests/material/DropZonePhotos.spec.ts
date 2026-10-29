import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DropZone from '@/material/DropZone.vue'

// F19 AC-14 (D2): con fotos activas la misma zona acepta JPEG y PNG y avisa de la privacidad; sin ellas, nada cambia respecto a F16
describe('DropZone con fotos', () => {
  const input = (w: ReturnType<typeof mount>) => w.get('[data-testid="file-input"]')

  it('does not offer photos by default: same accepted types and no notice', () => {
    const w = mount(DropZone)

    expect(input(w).attributes('accept')).toBe('.pdf,.docx,.txt,.md')
    expect(w.find('[data-testid="photo-notice"]').exists()).toBe(false)
  })

  it('accepts JPEG and PNG when photos are on, keeping the document types', () => {
    const w = mount(DropZone, { props: { photos: true } })

    const accept = input(w).attributes('accept') ?? ''
    for (const type of ['.pdf', '.docx', '.txt', '.md', 'image/jpeg', 'image/png']) expect(accept).toContain(type)
    expect(accept).not.toContain('image/*')
    expect(accept).not.toContain('webp')
  })

  it('never sets capture: on a phone it would stop the user from choosing a PDF', () => {
    const w = mount(DropZone, { props: { photos: true } })

    expect(input(w).attributes('capture')).toBeUndefined()
  })

  it('warns inside the zone that the photo goes to an AI service and to avoid personal data', () => {
    const w = mount(DropZone, { props: { photos: true } })

    const notice = w.get('[data-testid="photo-notice"]')
    expect(notice.text()).toContain('servicio de IA')
    expect(notice.text().toLowerCase()).toContain('datos personales')
    expect(w.get('[data-testid="drop-zone"]').text()).toContain('foto')
  })

  it('keeps the real file input as the only control and still emits the chosen file', async () => {
    const w = mount(DropZone, { props: { photos: true } })
    const file = new File([new Uint8Array(10)], 'apuntes.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input(w).element, 'files', { value: [file], configurable: true })

    await input(w).trigger('change')

    expect(w.emitted('choose')).toEqual([[file]])
    expect(input(w).classes()).toContain('visually-hidden')
  })
})
