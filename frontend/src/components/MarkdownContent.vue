<script lang="ts">
import { defineComponent, h, type VNode, type VNodeArrayChildren } from 'vue'
import { lexer, type Token } from 'marked'

/**
 * Pinta Markdown escrito por un modelo SIN producir HTML (ADR-45). La librería solo LEE el texto en tokens; aquí cada token se convierte en un elemento
 * Vue de una lista cerrada. El HTML crudo, las imágenes y los enlaces que no sean http(s) salen como texto, y la profundidad y el tamaño están acotados.
 * No se escribe ninguna cadena de HTML: lo que no se reconoce se pinta como texto.
 */
const MAX_SOURCE_CHARS = 60_000
const MAX_BLOCKS = 2_000
const MAX_DEPTH = 12

type Tok = Token & { tokens?: Token[]; text?: string; raw: string }

/** marked escapa el texto de sus tokens para producir HTML; aquí se pinta como texto, así que se deshace. */
function decode(text: string | undefined): string {
  return (text ?? '').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&')
}

/** Solo http y https; cualquier otro esquema (javascript:, data:, file:, mailto:...), una ruta relativa o una dirección rota no es un enlace. */
function safeHref(raw: string | undefined): string | null {
  try {
    const url = new URL(decode(raw))
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}

function inline(tokens: Token[] | undefined, depth: number): VNodeArrayChildren {
  const out: VNodeArrayChildren = []
  for (const token of (tokens ?? []) as Tok[]) {
    if (depth > MAX_DEPTH) {
      out.push(decode(token.text ?? token.raw))
      continue
    }
    switch (token.type) {
      case 'strong':
        out.push(h('strong', inline(token.tokens, depth + 1)))
        break
      case 'em':
        out.push(h('em', inline(token.tokens, depth + 1)))
        break
      case 'del':
        out.push(h('del', inline(token.tokens, depth + 1)))
        break
      case 'codespan':
        out.push(h('code', decode(token.text)))
        break
      case 'br':
        out.push(h('br'))
        break
      case 'link': {
        const href = safeHref((token as unknown as { href?: string }).href)
        const children = inline(token.tokens, depth + 1)
        out.push(href ? h('a', { href, rel: 'noopener noreferrer nofollow', target: '_blank' }, children) : h('span', children))
        break
      }
      case 'image': // nunca se carga una imagen: solo queda su texto alternativo
        out.push(decode(token.text))
        break
      case 'html': // HTML crudo: se muestra como texto, nunca como elemento
        out.push(token.raw)
        break
      case 'escape':
        out.push(decode(token.text))
        break
      case 'text':
        if (token.tokens && token.tokens.length > 0) out.push(...inline(token.tokens, depth + 1))
        else out.push(decode(token.text ?? token.raw))
        break
      default:
        out.push(decode(token.text ?? token.raw))
    }
  }
  return out
}

function blocks(tokens: Token[] | undefined, depth: number): VNode[] {
  const out: VNode[] = []
  for (const token of (tokens ?? []) as Tok[]) {
    if (out.length >= MAX_BLOCKS) break
    const node = block(token, depth)
    if (node) out.push(node)
  }
  return out
}

function block(token: Tok, depth: number): VNode | null {
  if (depth > MAX_DEPTH) return h('p', decode(token.text ?? token.raw).trim())
  switch (token.type) {
    case 'space':
    case 'def':
      return null
    case 'heading': {
      const level = Math.min(6, Math.max(1, (token as unknown as { depth: number }).depth))
      return h(`h${level}`, inline(token.tokens, depth + 1))
    }
    case 'paragraph':
      return h('p', inline(token.tokens, depth + 1))
    case 'text': // un párrafo «apretado» dentro de una lista o una cita
      return h('p', token.tokens ? inline(token.tokens, depth + 1) : decode(token.text ?? token.raw))
    case 'blockquote':
      return h('blockquote', blocks(token.tokens, depth + 1))
    case 'list': {
      const list = token as unknown as { ordered: boolean; start: number | ''; items: Tok[] }
      const items = list.items.slice(0, MAX_BLOCKS).map((item) => h('li', blocks(item.tokens, depth + 1)))
      return list.ordered ? h('ol', { start: typeof list.start === 'number' && list.start !== 1 ? list.start : undefined }, items) : h('ul', items)
    }
    case 'code':
      return h('pre', [h('code', token.text ?? '')])
    case 'hr':
      return h('hr')
    case 'table': {
      const table = token as unknown as { header: { tokens: Token[] }[]; rows: { tokens: Token[] }[][] }
      return h('div', { class: 'table-wrap' }, [
        h('table', [
          h('thead', [h('tr', table.header.map((cell) => h('th', inline(cell.tokens, depth + 1))))]),
          h('tbody', table.rows.slice(0, MAX_BLOCKS).map((row) => h('tr', row.map((cell) => h('td', inline(cell.tokens, depth + 1)))))),
        ]),
      ])
    }
    case 'html': // un bloque de HTML crudo se muestra como texto
      return h('p', { class: 'raw-html' }, token.raw.trim())
    default:
      return h('p', decode(token.text ?? token.raw).trim())
  }
}

export default defineComponent({
  name: 'MarkdownContent',
  props: { source: { type: String, default: '' } },
  setup(props) {
    return () => {
      const source = typeof props.source === 'string' ? props.source : ''
      const cut = source.length > MAX_SOURCE_CHARS
      let children: VNode[]
      try {
        children = blocks(lexer(cut ? source.slice(0, MAX_SOURCE_CHARS) : source), 1)
      } catch {
        children = source ? [h('p', source.slice(0, MAX_SOURCE_CHARS))] : [] // si la lectura falla, el texto se muestra tal cual
      }
      if (cut || children.length >= MAX_BLOCKS) children.push(h('p', { class: 'muted' }, 'El texto es muy largo y se ha recortado.'))
      return h('div', { class: 'markdown', 'data-testid': 'markdown' }, children)
    }
  },
})
</script>
