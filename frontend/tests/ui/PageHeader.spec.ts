import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { BookOpenText } from 'lucide-vue-next'
import PageHeader from '@/ui/PageHeader.vue'

// F17 AC-7: cabecera con la burbuja de la zona, el título y las acciones
describe('PageHeader', () => {
  it('renders the icon bubble, the title slot and the actions slot', () => {
    const wrapper = mount(PageHeader, {
      props: { icon: BookOpenText },
      slots: { default: () => 'Mi material', actions: () => 'Volver' },
    })

    expect(wrapper.find('[data-testid="page-header"]').exists()).toBe(true)
    expect(wrapper.find('.page-bubble svg').exists()).toBe(true)
    expect(wrapper.find('.page-bubble').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).toContain('Mi material')
    expect(wrapper.text()).toContain('Volver')
  })

  it('works without an icon', () => {
    const wrapper = mount(PageHeader, { slots: { default: () => 'Hola' } })

    expect(wrapper.find('.page-bubble').exists()).toBe(false)
    expect(wrapper.text()).toContain('Hola')
  })

  it('keeps the heading given by the view (hostile text stays text)', () => {
    const wrapper = mount(PageHeader, { slots: { default: () => '<h1>Curso <img src=x onerror=1></h1>' } })

    expect(wrapper.find('img').exists()).toBe(false)
  })
})
