import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TopeMeter from '@/results/TopeMeter.vue'

// F15 AC-2 (D3): el medidor del tope, con el estado dicho en palabras y con icono (nunca solo con color)
describe('TopeMeter', () => {
  const meter = (spendUsd: number | undefined, limitUsd: number | undefined, reached = false) =>
    mount(TopeMeter, { props: { label: 'Tu tope de este mes', spendUsd, limitUsd, reached } })
  const state = (w: ReturnType<typeof meter>) => w.get('[data-testid="tope-state"]')

  it('shows the spend against the limit, the percent and a progress bar', () => {
    const w = meter(0.5, 1)

    expect(w.text()).toContain('Tu tope de este mes')
    expect(w.text()).toContain('0,50')
    expect(w.text()).toContain('1,00')
    expect(w.text()).toContain('50 %')
    expect(w.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('50')
  })

  it.each([[0, 'vas bien'], [0.79, 'vas bien'], [0.8, 'cerca del tope'], [0.99, 'cerca del tope'], [1, 'tope alcanzado'], [1.4, 'tope alcanzado']])(
    'says in words where it is when the spend is %s of 1 USD',
    (spend, words) => {
      const w = meter(spend, 1)

      expect(state(w).text().toLowerCase()).toContain(words)
    },
  )

  it('says it with an icon and a class too, not only with a colour', () => {
    const w = meter(0.85, 1)

    expect(state(w).find('svg[aria-hidden="true"]').exists()).toBe(true)
    expect(w.classes().join(' ')).toMatch(/warn/)
    expect(meter(0.1, 1).classes().join(' ')).toMatch(/ok/)
    expect(meter(2, 1).classes().join(' ')).toMatch(/bad/)
  })

  it('never draws the bar past 100 %', () => {
    const w = meter(3, 1)

    expect(w.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    expect(w.text()).not.toContain('300 %')
  })

  it('counts the limit as reached when the server says so, whatever the figures', () => {
    expect(state(meter(0.5, 1, true)).text().toLowerCase()).toContain('tope alcanzado')
  })

  it('does not divide by zero with a limit of 0 or no limit', () => {
    for (const w of [meter(0, 0), meter(0.2, 0), meter(0.2, undefined), meter(undefined, undefined)]) {
      expect(w.html()).not.toContain('NaN')
      expect(w.html()).not.toContain('Infinity')
    }
    expect(state(meter(0, 0)).text().toLowerCase()).toContain('tope alcanzado')
  })

  it('writes no colour into the markup', () => {
    expect(meter(0.5, 1).html()).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgb\(|hsl\(/)
  })
})
