import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownContent from '@/components/MarkdownContent.vue'

// AC-13 (ADR-45): el Markdown lo escribe un modelo y puede intentar cualquier cosa; se pinta sin HTML y con una lista cerrada de elementos
const render = (source: string) => mount(MarkdownContent, { props: { source } })

// Lo que importa es que ningún ELEMENTO lleve un manejador de eventos; el HTML crudo se muestra como texto y puede nombrarlos (p. ej. «onclick=»)
const hasHandlerAttribute = (root: Element) => [root, ...root.querySelectorAll('*')].some((el) => [...el.attributes].some((a) => /^on/i.test(a.name)))

describe('MarkdownContent: lo que sí se pinta', () => {
  it('renders headings, paragraphs and emphasis', () => {
    const w = render('# Título\n\nUn párrafo con **negrita** y *cursiva*.\n\n## Subtítulo')

    expect(w.find('h1').text()).toBe('Título')
    expect(w.find('h2').text()).toBe('Subtítulo')
    expect(w.find('p').text()).toContain('Un párrafo con negrita y cursiva.')
    expect(w.find('strong').text()).toBe('negrita')
    expect(w.find('em').text()).toBe('cursiva')
  })

  it('renders ordered and unordered lists', () => {
    const w = render('- uno\n- dos\n\n1. primero\n2. segundo')

    expect(w.findAll('ul > li').map((li) => li.text())).toEqual(['uno', 'dos'])
    expect(w.findAll('ol > li').map((li) => li.text())).toEqual(['primero', 'segundo'])
  })

  it('renders inline code, code blocks and quotes', () => {
    const w = render('Usa `x = 1` así.\n\n```java\nSystem.out.println(1);\n```\n\n> una cita')

    expect(w.find('p code').text()).toBe('x = 1')
    expect(w.find('pre code').text()).toContain('System.out.println(1);')
    expect(w.find('blockquote').text()).toContain('una cita')
  })

  it('renders tables with their header and cells', () => {
    const w = render('| Persona | Forma |\n|---|---|\n| I | am |\n| you | are |')

    expect(w.findAll('th').map((th) => th.text())).toEqual(['Persona', 'Forma'])
    expect(w.findAll('tbody tr')).toHaveLength(2)
    expect(w.findAll('tbody tr')[1].findAll('td').map((td) => td.text())).toEqual(['you', 'are'])
  })

  it('renders an https link with a safe rel and target', () => {
    const w = render('Más en [la web](https://example.com/tema).')

    const a = w.find('a')
    expect(a.attributes('href')).toBe('https://example.com/tema')
    expect(a.attributes('rel')).toContain('noopener')
    expect(a.attributes('rel')).toContain('noreferrer')
    expect(a.attributes('target')).toBe('_blank')
    expect(a.text()).toBe('la web')
  })

  it('keeps citation markers and LaTeX as plain text', () => {
    const w = render('Según el material [1], la fórmula $x^2 + y^2 = z^2$ se cumple.')

    expect(w.text()).toContain('[1]')
    expect(w.text()).toContain('$x^2 + y^2 = z^2$')
  })
})

describe('MarkdownContent: entradas hostiles', () => {
  it('shows a script tag as text and never as an element', () => {
    const w = render('Hola\n\n<script>alert(1)</script>\n\nAdiós')

    expect(w.find('script').exists()).toBe(false)
    expect(w.text()).toContain('<script>alert(1)</script>')
  })

  it('shows an img tag with an event handler as text', () => {
    const w = render('<img src=x onerror=alert(1)>')

    expect(w.find('img').exists()).toBe(false)
    expect(w.html()).not.toMatch(/<img/i)
    expect(w.text()).toContain('<img src=x onerror=alert(1)>')
  })

  it('shows raw html blocks and inline html as text', () => {
    const w = render('<div onclick="x()">bloque</div>\n\nTexto con <b onmouseover="y()">negrita</b> dentro')

    expect(w.find('div[onclick]').exists()).toBe(false)
    expect(w.find('b').exists()).toBe(false)
    expect(hasHandlerAttribute(w.element)).toBe(false)
  })

  it('does not turn a javascript link into a link', () => {
    const w = render('[clic](javascript:alert(1)) y [otro](JavaScript:alert(2))')

    expect(w.find('a').exists()).toBe(false)
    expect(w.text()).toContain('clic')
    expect(w.html()).not.toContain('javascript:')
  })

  it.each([
    ['data', '[x](data:text/html,<b>x</b>)'],
    ['vbscript', '[x](vbscript:msgbox)'],
    ['file', '[x](file:///etc/passwd)'],
    ['protocol-relative', '[x](//evil.example/x)'],
    ['relative', '[x](/ruta/interna)'],
    ['mailto', '[x](mailto:a@b.c)'],
  ])('does not link a %s URL', (_name, source) => {
    expect(render(source).find('a').exists()).toBe(false)
  })

  it('never renders an image, neither remote nor inline: only its alt text remains', () => {
    const w = render('![texto alternativo](https://evil.example/pixel.png)\n\n![x](data:image/png;base64,AAAA)')

    expect(w.find('img').exists()).toBe(false)
    expect(w.html()).not.toContain('evil.example/pixel')
    expect(w.text()).toContain('texto alternativo')
  })

  it('never produces an element with an event handler attribute whatever the input', () => {
    const w = render('[a](https://ok.example "title\\" onmouseover=\\"x()")\n\n**<span onclick=x()>y</span>**')

    expect(hasHandlerAttribute(w.element)).toBe(false)
  })

  it('survives absurdly nested lists and quotes without crashing', () => {
    const deepList = Array.from({ length: 60 }, (_, i) => `${'  '.repeat(i)}- nivel ${i}`).join('\n')
    const deepQuote = '>'.repeat(80) + ' cita'

    expect(() => render(deepList)).not.toThrow()
    expect(() => render(deepQuote)).not.toThrow()
    expect(render(deepList).text()).toContain('nivel 0')
  })

  it('caps the size of what it renders', () => {
    const huge = Array.from({ length: 20000 }, (_, i) => `línea ${i}`).join('\n\n')

    const w = render(huge)

    expect(w.findAll('p').length).toBeLessThan(20000)
  })

  it('renders nothing for empty or non-string input without failing', () => {
    expect(render('').text()).toBe('')
    expect(mount(MarkdownContent, { props: { source: undefined as unknown as string } }).text()).toBe('')
  })
})
