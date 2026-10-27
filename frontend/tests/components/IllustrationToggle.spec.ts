import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import IllustrationToggle from '@/components/IllustrationToggle.vue'

const offer = { available: true, pricePerImageUsd: 0.05, maxTopics: 12 }
const render = (props: Record<string, unknown> = {}) => mount(IllustrationToggle, { props: { offer, modelValue: false, ...props } })

// F12 AC-3: el interruptor «Con ilustraciones» del asistente, con el coste estimado
describe('IllustrationToggle', () => {
  it('renders nothing when the offer is not available (no provider or a minor)', () => {
    expect(render({ offer: { available: false, pricePerImageUsd: 0, maxTopics: 12 } }).find('[data-testid="illustration-toggle"]').exists()).toBe(false)
    expect(render({ offer: null }).find('[data-testid="illustration-toggle"]').exists()).toBe(false)
  })

  it('is a labelled checkbox that is off by default', () => {
    const w = render()

    const box = w.find('input[type="checkbox"]')
    expect(box.exists()).toBe(true)
    expect((box.element as HTMLInputElement).checked).toBe(false)
    expect(w.find('label').text()).toContain('Con ilustraciones')
    expect(w.find('label').attributes('for')).toBe(box.attributes('id'))
  })

  it('emits the choice when it is used', async () => {
    const w = render()

    await w.find('input[type="checkbox"]').setValue(true)

    expect(w.emitted('update:modelValue')?.[0]).toEqual([true])
  })

  it('shows the price per image and the most it can cost, in Spanish format, and says it is an estimate', () => {
    const text = render().find('[data-testid="illustration-cost"]').text()

    expect(text).toContain('0,05')
    expect(text).toContain('0,60')
    expect(text).toContain('12')
    expect(text.toLowerCase()).toContain('estimación')
    expect(text.toLowerCase()).toContain('tope')
  })

  it('says it is one image per topic', () => {
    expect(render().text().toLowerCase()).toContain('por tema')
  })

  it('formats small prices without losing precision', () => {
    const text = render({ offer: { available: true, pricePerImageUsd: 0.005, maxTopics: 10 } }).find('[data-testid="illustration-cost"]').text()

    expect(text).toMatch(/0,005|0,01/)
    expect(text).toContain('0,05')
  })

  it('keeps the checked state it is given', () => {
    const w = render({ modelValue: true })

    expect((w.find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
  })
})
