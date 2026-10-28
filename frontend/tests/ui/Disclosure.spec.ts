import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Disclosure from '@/ui/Disclosure.vue'

// F16 AC-3 (D3): un bloque que se abre y se cierra con un botón y dice su estado a los lectores de pantalla
describe('Disclosure', () => {
  const make = (open: boolean) =>
    mount(Disclosure, { props: { label: 'Añadir un menor', open, 'onUpdate:open': (v: boolean) => w.setProps({ open: v }) }, slots: { default: '<p data-testid="inside">Contenido</p>' } })
  let w: ReturnType<typeof make>

  it('is a real button with the label and the open state in aria-expanded', () => {
    w = make(false)

    const button = w.get('button')
    expect(button.attributes('type')).toBe('button')
    expect(button.text()).toContain('Añadir un menor')
    expect(button.attributes('aria-expanded')).toBe('false')
  })

  it('points to its region with aria-controls', () => {
    w = make(true)

    const id = w.get('button').attributes('aria-controls')
    expect(id).toBeTruthy()
    expect(w.get(`#${id}`).find('[data-testid="inside"]').exists()).toBe(true)
  })

  it('hides the region when closed but keeps what is inside in the page, and shows it when open', async () => {
    w = make(false)
    expect(w.find('[data-testid="inside"]').exists()).toBe(true) // v-show: no se pierde lo escrito
    expect((w.get('[data-testid="inside"]').element.closest('[id]') as HTMLElement).style.display).toBe('none')

    await w.get('button').trigger('click')

    expect(w.get('button').attributes('aria-expanded')).toBe('true')
    expect((w.get('[data-testid="inside"]').element.closest('[id]') as HTMLElement).style.display).not.toBe('none')
  })

  it('emits update:open with the new state on every click and closes again', async () => {
    w = make(false)

    await w.get('button').trigger('click')
    await w.get('button').trigger('click')

    expect(w.emitted('update:open')).toEqual([[true], [false]])
    expect(w.get('button').attributes('aria-expanded')).toBe('false')
  })

  it('renders a hostile label as plain text', () => {
    w = mount(Disclosure, { props: { label: '<img src=x onerror=alert(1)>', open: false } })

    expect(w.text()).toContain('<img src=x onerror=alert(1)>')
    expect(w.find('img').exists()).toBe(false)
  })
})
