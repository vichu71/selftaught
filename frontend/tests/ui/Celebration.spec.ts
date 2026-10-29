import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Celebration from '@/ui/Celebration.vue'

// F17 AC-10, ADR-97: el texto siempre sale; el confeti es decoración
describe('Celebration', () => {
  it('always says «¡Hecho!» in a polite status', () => {
    const wrapper = mount(Celebration)

    const status = wrapper.find('[role="status"]')
    expect(status.exists()).toBe(true)
    expect(status.text()).toContain('¡Hecho!')
  })

  it('draws twelve decorative confetti pieces hidden from assistive technology', () => {
    const wrapper = mount(Celebration)

    expect(wrapper.findAll('.confetti-piece')).toHaveLength(12)
    expect(wrapper.find('.confetti').attributes('aria-hidden')).toBe('true')
  })

  it('accepts a custom message as text', () => {
    const wrapper = mount(Celebration, { props: { message: '<b>Genial</b>' } })

    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.text()).toContain('<b>Genial</b>')
  })
})
