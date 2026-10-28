import { describe, expect, it } from 'vitest'
import StudentCard from '@/components/StudentCard.vue'
import { adult, mountApp } from '../helpers'

const hugo = { id: 's1', displayName: 'Hugo', loginId: 'hugo-4k7', status: 'ACTIVE' }
const lola = { id: 's2', displayName: 'Lola', loginId: 'lola', status: 'DEACTIVATED' }

// F16 AC-2 (D2): cada menor es una tarjeta con su estado en palabras e icono y sus acciones como botones
describe('StudentCard', () => {
  const open = (student: object = hugo) => mountApp(StudentCard, { user: adult, route: '/', props: { student } })

  it('is a card with the name, the access id as text and the state in words with an icon', async () => {
    const { wrapper } = await open()

    expect(wrapper.attributes('data-testid')).toBe('student-s1')
    expect(wrapper.classes()).toContain('student-card')
    expect(wrapper.text()).toContain('Hugo')
    expect(wrapper.text()).toContain('hugo-4k7')
    expect(wrapper.get('[data-testid="student-status-s1"]').text()).toContain('Activo')
    expect(wrapper.get('[data-testid="student-status-s1"]').find('svg[aria-hidden="true"]').exists()).toBe(true)
  })

  it('has «Ver actividad» as a button-shaped link to the guardian panel of that minor', async () => {
    const { wrapper } = await open()

    const link = wrapper.get('[data-testid="student-activity-s1"]')
    expect(link.attributes('href')).toBe('/alumnos/s1')
    expect(link.classes()).toContain('ui-button')
    expect(link.text()).toBe('Ver actividad')
  })

  it('asks to change the password and to deactivate through events, with the id', async () => {
    const { wrapper } = await open()

    await wrapper.get('[data-testid="reset-s1"]').trigger('click')
    await wrapper.get('[data-testid="deactivate-s1"]').trigger('click')

    expect(wrapper.emitted('reset')).toEqual([['s1']])
    expect(wrapper.emitted('toggle')).toEqual([['s1']])
    expect(wrapper.find('[data-testid="reactivate-s1"]').exists()).toBe(false)
  })

  it('says «De baja» and offers «Reactivar» instead of «Dar de baja» for a deactivated minor', async () => {
    const { wrapper } = await open(lola)

    expect(wrapper.get('[data-testid="student-status-s2"]').text()).toContain('De baja')
    expect(wrapper.find('[data-testid="deactivate-s2"]').exists()).toBe(false)
    await wrapper.get('[data-testid="reactivate-s2"]').trigger('click')
    expect(wrapper.emitted('toggle')).toEqual([['s2']])
  })

  it('renders a hostile name as plain text', async () => {
    const { wrapper } = await open({ ...hugo, displayName: '<img src=x onerror=alert(1)>' })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
