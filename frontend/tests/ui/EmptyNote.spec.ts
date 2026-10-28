import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyNote from '@/ui/EmptyNote.vue'

// F16 AC-8 (D7): un «todavía no hay nada» amable, con la planta o la mascota de F11
describe('EmptyNote', () => {
  it('shows the message with a sprouting seed by default', () => {
    const w = mount(EmptyNote, { props: { message: 'Todavía no has subido ningún documento.' } })

    expect(w.text()).toContain('Todavía no has subido ningún documento.')
    expect(w.classes()).toContain('empty-note')
    expect(w.get('svg.plant').attributes('data-stage')).toBe('seed')
  })

  it('can show the mascot instead of the plant', () => {
    const w = mount(EmptyNote, { props: { message: 'Aún no has dado de alta a nadie.', art: 'mascot' } })

    expect(w.find('svg.mascot').exists()).toBe(true)
    expect(w.find('svg.plant').exists()).toBe(false)
  })

  it('keeps the illustration out of the reading order and announces the message politely', () => {
    const w = mount(EmptyNote, { props: { message: 'Nada por aquí.' } })

    expect(w.get('svg').attributes('aria-hidden')).toBe('true')
    expect(w.attributes('role')).toBe('status')
  })

  it('lets the test id pass through', () => {
    const w = mount(EmptyNote, { props: { message: 'Nada.' }, attrs: { 'data-testid': 'no-documents' } })

    expect(w.attributes('data-testid')).toBe('no-documents')
  })

  it('renders a hostile message as plain text', () => {
    const w = mount(EmptyNote, { props: { message: '<img src=x onerror=alert(1)>' } })

    expect(w.text()).toContain('<img src=x onerror=alert(1)>')
    expect(w.find('img').exists()).toBe(false)
  })
})
