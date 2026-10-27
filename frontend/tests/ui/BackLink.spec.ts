import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import BackLink from '@/ui/BackLink.vue'
import { adult, mountApp } from '../helpers'

const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8')

// F15 AC-1 (D1, D2): el botón «Volver» con flecha que sustituye al enlace subrayado de cinco pantallas
describe('BackLink', () => {
  const open = (props: Record<string, unknown> = {}, attrs: Record<string, string> = {}) =>
    mountApp(BackLink, { user: adult, route: '/resultados', props: { ...props, ...attrs } })

  it('is a link to the start with a left arrow and the text «Inicio»', async () => {
    const { wrapper } = await open()

    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/')
    expect(wrapper.text()).toBe('Inicio')
    expect(wrapper.find('svg[aria-hidden="true"]').exists()).toBe(true)
    expect(wrapper.classes()).toContain('back-link')
  })

  it('has an accessible name that says where it goes', async () => {
    const { wrapper } = await open()

    expect(wrapper.attributes('aria-label')).toBe('Volver al inicio')
  })

  it('takes its text and its accessible name from its props, for «Cancelar»', async () => {
    const { wrapper } = await open({ label: 'Cancelar', ariaLabel: 'Cancelar y volver al inicio' })

    expect(wrapper.text()).toBe('Cancelar')
    expect(wrapper.attributes('aria-label')).toBe('Cancelar y volver al inicio')
  })

  it('goes to another place when asked to', async () => {
    const { wrapper } = await open({ to: '/material' })

    expect(wrapper.attributes('href')).toBe('/material')
  })

  it('lets the test id pass through to the link', async () => {
    const { wrapper } = await open({ 'data-testid': 'back-home' })

    expect(wrapper.attributes('data-testid')).toBe('back-home')
  })

  it('looks like a button: no underline and a tap target of 44 px, defined with tokens', () => {
    const css = read('src/styles/components.css')
    const rule = css.match(/\.back-link\s*\{[^}]*\}/)

    expect(rule, 'hay una regla .back-link en components.css').not.toBeNull()
    expect(rule![0]).toMatch(/text-decoration:\s*none/)
    expect(rule![0]).toMatch(/min-height:\s*var\(--size-tap\)/)
    expect(rule![0]).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(/)
  })
})

// F15 AC-1 (D2): las cinco pantallas usan el mismo componente y conservan sus data-testid y su destino
describe('the five screens use BackLink', () => {
  const screens: [string, string, string, string][] = [
    ['ResultsView', 'src/views/ResultsView.vue', 'back-home', 'Volver al inicio'],
    ['CourseStatusView', 'src/views/CourseStatusView.vue', 'back-home', 'Volver al inicio'],
    ['MaterialView', 'src/views/MaterialView.vue', 'back-home', 'Volver al inicio'],
    ['RedeemView', 'src/views/RedeemView.vue', 'back-home', 'Volver al inicio'],
    ['NewCourseView', 'src/views/NewCourseView.vue', 'cancel-home', 'Cancelar'],
  ]

  it.each(screens)('%s shows a BackLink with its test id and no loose underlined link', (_name, file, testid, label) => {
    const source = read(file)

    expect(source).toMatch(/import BackLink from '@\/ui\/BackLink\.vue'/)
    expect(source).toMatch(new RegExp(`<BackLink[^>]*data-testid="${testid}"`))
    expect(source).not.toMatch(/<RouterLink[^>]*data-testid="(back-home|cancel-home)"/)
    if (label === 'Cancelar') expect(source).toMatch(/<BackLink[^>]*label="Cancelar"/)
    else expect(source).not.toMatch(/>\s*Volver al inicio\s*<\/RouterLink>/)
  })
})
