import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { BookOpenText } from 'lucide-vue-next'
import ActionCard from '@/ui/ActionCard.vue'
import { adult, mountApp } from '../helpers'

const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8')

// F16 AC-1 (D1): un atajo es una tarjeta que es el enlace entero, con icono, título y descripción
describe('ActionCard', () => {
  const open = (props: Record<string, unknown> = {}) =>
    mountApp(ActionCard, { user: adult, route: '/', props: { to: '/material', title: 'Mi material', description: 'Sube tus apuntes.', icon: BookOpenText, ...props } })

  it('is one link to its destination with the icon, the title and the description inside', async () => {
    const { wrapper } = await open()

    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/material')
    expect(wrapper.classes()).toContain('action-card')
    expect(wrapper.text()).toContain('Mi material')
    expect(wrapper.text()).toContain('Sube tus apuntes.')
    expect(wrapper.find('svg[aria-hidden="true"]').exists()).toBe(true)
  })

  it('lets the test id pass through to the link', async () => {
    const { wrapper } = await open({ 'data-testid': 'material-link' })

    expect(wrapper.attributes('data-testid')).toBe('material-link')
  })

  it('renders a hostile title and description as plain text', async () => {
    const { wrapper } = await open({ title: '<img src=x onerror=alert(1)>', description: '<b>negrita</b>' })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.text()).toContain('<b>negrita</b>')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('b').exists()).toBe(false)
  })

  it('looks like a card, not like an underlined link: a tap target of 44 px and no underline, with tokens', () => {
    const css = read('src/styles/components.css')
    const rule = css.match(/\.action-card\s*\{[^}]*\}/)

    expect(rule, 'hay una regla .action-card en components.css').not.toBeNull()
    expect(rule![0]).toMatch(/text-decoration:\s*none/)
    expect(rule![0]).toMatch(/min-height:\s*var\(--size-tap\)/)
    expect(rule![0]).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(/)
  })
})
