import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BarList from '@/owner/BarList.vue'
import DataTable from '@/owner/DataTable.vue'
import LineChart from '@/owner/LineChart.vue'
import StatCard from '@/owner/StatCard.vue'

const HOSTILE = '<img src=x onerror=alert(1)>'
const days = (n: number) => Array.from({ length: n }, (_, i) => ({ label: `2026-10-${String(i + 1).padStart(2, '0')}`, value: i % 3 }))

// F14 AC-12, AC-13 (ADR-90): las gráficas son elementos Vue y SVG, con la tabla equivalente debajo y sin v-html
describe('LineChart', () => {
  const chart = (points = days(7), extra: Record<string, unknown> = {}) => mount(LineChart, { props: { points, title: 'Coste por día', unit: 'USD', ...extra } })

  it('draws an accessible SVG with a name and one path for the series', () => {
    const w = chart()

    const svg = w.get('svg')
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toContain('Coste por día')
    expect(w.findAll('[data-testid="line-path"]')).toHaveLength(1)
    expect(w.get('[data-testid="line-path"]').attributes('d')).toMatch(/^M[\d.\s,LM-]+$/)
  })

  it('always shows the equivalent table with one row per point', () => {
    const w = chart(days(5))

    const rows = w.get('[data-testid="chart-table"]').findAll('tbody tr')
    expect(rows).toHaveLength(5)
    expect(rows[0].text()).toContain('2026-10-01')
    expect(w.get('[data-testid="chart-table"]').find('caption').text()).toContain('Coste por día')
  })

  it('draws a flat line without NaN when every value is zero or there is a single point', () => {
    for (const points of [days(4).map((p) => ({ ...p, value: 0 })), [{ label: 'a', value: 3 }]]) {
      const w = chart(points)

      expect(w.html()).not.toContain('NaN')
      expect(w.html()).not.toContain('Infinity')
    }
  })

  it('shows an empty state with text and no line when there is no data', () => {
    const w = chart([])

    expect(w.find('[data-testid="chart-empty"]').exists()).toBe(true)
    expect(w.find('[data-testid="line-path"]').exists()).toBe(false)
  })

  it('renders hostile labels as plain text and never as elements', () => {
    const w = chart([{ label: HOSTILE, value: 1 }, { label: 'b', value: 2 }])

    expect(w.text()).toContain(HOSTILE)
    expect(w.find('img').exists()).toBe(false)
  })

  it('takes every colour from classes (tokens) and writes no colour into the markup', () => {
    const html = chart().html()

    expect(html).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(html).not.toMatch(/(fill|stroke)="(?!none|currentColor)[^"]*"/)
    expect(html).not.toMatch(/rgb\(|hsl\(/)
    expect(html).not.toContain('<image')
    expect(html).not.toContain('foreignObject')
  })

  it('says the highest value and where it happened in words, not only with the shape', () => {
    const w = chart([{ label: 'a', value: 1 }, { label: 'b', value: 9 }, { label: 'c', value: 2 }])

    expect(w.get('[data-testid="chart-summary"]').text()).toMatch(/9/)
    expect(w.get('[data-testid="chart-summary"]').text()).toMatch(/b/)
  })
})

describe('BarList', () => {
  const list = (items = [{ label: 'claude-haiku', value: 3.9 }, { label: 'gemini', value: 2.2 }]) => mount(BarList, { props: { items, title: 'Por modelo', unit: 'USD' } })

  it('lists every item with its label and its value as text', () => {
    const w = list()

    const rows = w.findAll('[data-testid="bar-item"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('claude-haiku')
    expect(rows[0].text()).toContain('3,90')
    expect(rows[1].text()).toContain('2,20')
  })

  it('scales the bars to the biggest item and never past 100 %', () => {
    const w = list()

    const widths = w.findAll('[data-testid="bar-fill"]').map((b) => parseFloat(b.attributes('style')!.match(/width:\s*([\d.]+)%/)![1]))
    expect(widths[0]).toBe(100)
    expect(widths[1]).toBeCloseTo((2.2 / 3.9) * 100, 0)
    expect(Math.max(...widths)).toBeLessThanOrEqual(100)
  })

  it('shows zero values as an empty bar and an empty list as a message', () => {
    const w = list([{ label: 'a', value: 0 }])
    expect(w.get('[data-testid="bar-fill"]').attributes('style')).toContain('width: 0%')

    expect(list([]).find('[data-testid="chart-empty"]').exists()).toBe(true)
  })

  it('renders hostile labels as plain text', () => {
    const w = list([{ label: HOSTILE, value: 1 }])

    expect(w.text()).toContain(HOSTILE)
    expect(w.find('img').exists()).toBe(false)
  })

  it('writes no colour into the markup', () => {
    expect(list().html()).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(/)
  })
})

describe('DataTable', () => {
  const columns = [{ key: 'name', label: 'Modelo' }, { key: 'usd', label: 'Coste' }]
  const table = (rows: Record<string, string | number | null>[]) => mount(DataTable, { props: { columns, rows, caption: 'Gasto por modelo' } })

  it('is a real table with a caption, header cells scoped to their column and one row per record', () => {
    const w = table([{ name: 'm1', usd: 1.5 }, { name: 'm2', usd: 2 }])

    expect(w.find('caption').text()).toBe('Gasto por modelo')
    expect(w.findAll('thead th').every((th) => th.attributes('scope') === 'col')).toBe(true)
    expect(w.findAll('tbody tr')).toHaveLength(2)
  })

  it('shows a dash for empty values and plain text for hostile ones', () => {
    const w = table([{ name: HOSTILE, usd: null }])

    expect(w.text()).toContain(HOSTILE)
    expect(w.find('img').exists()).toBe(false)
    expect(w.findAll('tbody td')[1].text()).toBe('—')
  })

  it('scrolls inside its own box so the page never overflows on a phone', () => {
    const w = table([{ name: 'm1', usd: 1 }])

    expect(w.get('[data-testid="data-table-scroll"]').classes()).toContain('table-scroll')
  })
})

describe('StatCard', () => {
  it('shows the label and the value', () => {
    const w = mount(StatCard, { props: { label: 'Hoy', value: '0,42 USD' } })

    expect(w.text()).toContain('Hoy')
    expect(w.text()).toContain('0,42 USD')
  })

  it('says the state in words and with an icon, not only with colour', () => {
    const w = mount(StatCard, { props: { label: 'Tope global', value: '91 %', tone: 'warn', status: 'Cerca del tope' } })

    expect(w.get('[data-testid="stat-status"]').text()).toBe('Cerca del tope')
    expect(w.find('svg[aria-hidden="true"]').exists()).toBe(true)
    expect(w.classes().join(' ')).toMatch(/warn/)
  })

  it('renders hostile text as text', () => {
    const w = mount(StatCard, { props: { label: HOSTILE, value: HOSTILE } })

    expect(w.find('img').exists()).toBe(false)
  })
})
