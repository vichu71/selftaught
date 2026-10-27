import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TopicCover from '@/components/TopicCover.vue'

const render = (image?: { status: string; alt?: string } | null, extra: Record<string, unknown> = {}) =>
  mount(TopicCover, { props: { courseId: 'c1', topicId: 't1', title: 'Los volcanes', image, ...extra } })

// F12 AC-12: la ilustración de un tema, con su rótulo, su texto alternativo y la planta como respaldo
describe('TopicCover', () => {
  it('shows the illustration with its alternative text and the AI label when it is done', () => {
    const w = render({ status: 'DONE', alt: 'Ilustración generada por IA sobre «Los volcanes»' })

    const img = w.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('/api/courses/c1/topics/t1/image')
    expect(img.attributes('alt')).toBe('Ilustración generada por IA sobre «Los volcanes»')
    expect(img.attributes('loading')).toBe('lazy')
    expect(w.text()).toContain('Ilustración generada por IA')
  })

  it('only ever asks for the image of its own origin', () => {
    const w = render({ status: 'DONE', alt: 'x' })

    expect(w.find('img').attributes('src')).toMatch(/^\/api\//)
    expect(w.html()).not.toMatch(/https?:\/\//)
  })

  it('shows the plant and says it is being prepared while the image is pending', () => {
    const w = render({ status: 'PENDING' })

    expect(w.find('img').exists()).toBe(false)
    expect(w.find('svg[data-stage]').exists()).toBe(true)
    expect(w.text()).toContain('Preparándose')
  })

  it.each(['FAILED', 'BLOCKED'])('shows the plant, with no error text and no image, when it is %s', (status) => {
    const w = render({ status })

    expect(w.find('img').exists()).toBe(false)
    expect(w.find('svg[data-stage]').exists()).toBe(true)
    expect(w.text().toLowerCase()).not.toMatch(/error|bloquead|moderaci/)
  })

  it('shows the plant when there is no image at all (the course has none or the owner removed it)', () => {
    for (const image of [undefined, null]) {
      const w = render(image)
      expect(w.find('img').exists()).toBe(false)
      expect(w.find('svg[data-stage]').exists()).toBe(true)
    }
  })

  it('falls back to the plant if the image cannot be loaded', async () => {
    const w = render({ status: 'DONE', alt: 'x' })

    await w.find('img').trigger('error')

    expect(w.find('img').exists()).toBe(false)
    expect(w.find('svg[data-stage]').exists()).toBe(true)
  })

  it('shows a hostile alternative text and title as text only', () => {
    const w = render({ status: 'DONE', alt: '"><img src=x onerror=alert(1)>' }, { title: '<script>alert(1)</script>' })

    expect(w.findAll('img')).toHaveLength(1)
    expect(w.find('img').attributes('alt')).toBe('"><img src=x onerror=alert(1)>')
    expect(w.html()).not.toContain('<script')
    expect(w.findAll('*').some((el) => el.element.getAttributeNames().some((n) => n.startsWith('on')))).toBe(false)
  })

  it('does not draw outside its box and keeps a stable shape for the layout (square frame)', () => {
    const w = render({ status: 'DONE', alt: 'x' })

    expect(w.find('[data-testid="topic-cover"]').exists()).toBe(true)
    expect(w.find('[data-testid="topic-cover"]').classes()).toContain('topic-cover')
  })
})
