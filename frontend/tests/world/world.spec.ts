import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Landscape from '@/world/Landscape.vue'
import Mascot from '@/world/Mascot.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'
import Wanderer from '@/world/Wanderer.vue'
import NavBar from '@/layouts/NavBar.vue'
import { adult, byId, mountApp } from '../helpers'

const STAGES = ['seed', 'sprout', 'tree', 'fruit'] as const

// F11 AC-3, AC-8: ilustraciones propias, ligeras y accesibles
describe('PlantIllustration', () => {
  it.each(STAGES)('draws the %s stage as an SVG that says which stage it is', (stage) => {
    const w = mount(PlantIllustration, { props: { stage } })

    expect(w.element.tagName.toLowerCase()).toBe('svg')
    expect(w.attributes('data-stage')).toBe(stage)
  })

  it('draws a different picture for each stage', () => {
    const html = STAGES.map((stage) => mount(PlantIllustration, { props: { stage } }).html())

    expect(new Set(html).size).toBe(4)
  })

  it('is an image with a name when it is given a label, and decorative when it is not', () => {
    const named = mount(PlantIllustration, { props: { stage: 'tree', label: 'Árbol: Historia antigua' } })
    expect(named.attributes('role')).toBe('img')
    expect(named.attributes('aria-label')).toBe('Árbol: Historia antigua')
    expect(named.attributes('aria-hidden')).toBeUndefined()

    const decorative = mount(PlantIllustration, { props: { stage: 'tree' } })
    expect(decorative.attributes('aria-hidden')).toBe('true')
    expect(decorative.attributes('role')).toBeUndefined()
  })

  it('shows a hostile label as text, never as markup', () => {
    const w = mount(PlantIllustration, { props: { stage: 'seed', label: '<img src=x onerror=alert(1)>' } })

    expect(w.find('img').exists()).toBe(false)
    expect(w.attributes('aria-label')).toBe('<img src=x onerror=alert(1)>')
  })

  it('has fruit only on the last stage', () => {
    for (const stage of STAGES) {
      const w = mount(PlantIllustration, { props: { stage } })
      expect(w.findAll('[data-part="fruit"]').length > 0, stage).toBe(stage === 'fruit')
    }
  })
})

describe('Landscape, Mascot and Wanderer', () => {
  it('the landscape is purely decorative', () => {
    const w = mount(Landscape)

    expect(w.attributes('aria-hidden')).toBe('true')
    expect(w.find('[data-part="sun"]').exists()).toBe(true)
    expect(w.find('[data-part="hill"]').exists()).toBe(true)
  })

  it('the mascot is decorative by default and can carry a name', () => {
    expect(mount(Mascot).attributes('aria-hidden')).toBe('true')
    const named = mount(Mascot, { props: { label: 'Study Lab' } })
    expect(named.attributes('role')).toBe('img')
    expect(named.attributes('aria-label')).toBe('Study Lab')
  })

  it('the wanderer is a decorative marker that can be placed with x and y', () => {
    const w = mount({ components: { Wanderer }, template: '<svg><Wanderer :x="40" :y="70" /></svg>' })

    expect(w.find('[data-testid="map-wanderer"]').exists()).toBe(true)
    expect(w.find('[data-testid="map-wanderer"]').attributes('aria-hidden')).toBe('true')
    expect(w.html()).toMatch(/40/)
  })

  it('none of them embeds external resources, scripts, foreignObject or raster images', () => {
    for (const c of [Landscape, Mascot, Wanderer, PlantIllustration]) {
      const html = mount(c, { props: { stage: 'tree' }, attachTo: undefined }).html()
      expect(html).not.toMatch(/<script|<image|<foreignObject|xlink:href|href="http|<img|url\(http/i)
    }
  })
})

// F11 AC-11: la mascota es la marca de la barra de navegación
describe('Navigation with the mascot', () => {
  it('shows the mascot next to the name, hidden from assistive technology (the name is already text)', async () => {
    const { wrapper } = await mountApp(NavBar, { user: adult, route: '/' })

    const brand = wrapper.find('.brand')
    expect(brand.text()).toContain('Study Lab')
    expect(byId(wrapper, 'nav-mascot').exists()).toBe(true)
    expect(byId(wrapper, 'nav-mascot').attributes('aria-hidden')).toBe('true')
  })
})
