import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import UiTag from '@/ui/UiTag.vue'

// F17 AC-9: un estado nunca se dice solo con color
describe('UiTag · estados', () => {
  it.each(['ok', 'warn', 'bad', 'info'] as const)('tone %s carries an icon next to its text', (tone) => {
    const wrapper = mount(UiTag, { props: { tone }, slots: { default: () => 'Texto' } })

    expect(wrapper.classes()).toContain(`ui-tag--${tone}`)
    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.text()).toContain('Texto')
  })

  it('keeps the plain tones without an icon (they carry no state)', () => {
    for (const tone of ['plain', 'now', 'accent'] as const) {
      const wrapper = mount(UiTag, { props: { tone }, slots: { default: () => 'Texto' } })
      expect(wrapper.find('svg').exists(), tone).toBe(false)
    }
  })

  it('supports the zone tones', () => {
    const wrapper = mount(UiTag, { props: { tone: 'material' }, slots: { default: () => 'Azul' } })

    expect(wrapper.classes()).toContain('ui-tag--material')
  })
})
