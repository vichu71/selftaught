import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import UiButton from '@/ui/UiButton.vue'
import UiDialog from '@/ui/UiDialog.vue'
import UiField from '@/ui/UiField.vue'
import UiNotice from '@/ui/UiNotice.vue'
import UiProgress from '@/ui/UiProgress.vue'
import UiState from '@/ui/UiState.vue'
import UiTabs from '@/ui/UiTabs.vue'
import UiTag from '@/ui/UiTag.vue'

const mounted: VueWrapper[] = []
const track = <T extends VueWrapper>(w: T) => (mounted.push(w), w)
afterEach(() => {
  mounted.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
})

// F10 AC-2: componentes base
describe('UiButton', () => {
  it('is a real button of type "button" by default and emits click', async () => {
    const w = track(mount(UiButton, { slots: { default: 'Guardar' } }))

    expect(w.element.tagName).toBe('BUTTON')
    expect(w.attributes('type')).toBe('button')
    await w.trigger('click')
    expect(w.emitted('click')).toHaveLength(1)
  })

  it('does not emit click when disabled', async () => {
    const w = track(mount(UiButton, { props: { disabled: true }, slots: { default: 'Guardar' } }))

    await w.trigger('click')

    expect(w.attributes('disabled')).toBeDefined()
    expect(w.emitted('click')).toBeUndefined()
  })

  it('reflects its variant in a class, never in inline colours', () => {
    for (const variant of ['primary', 'secondary', 'danger']) {
      const w = track(mount(UiButton, { props: { variant }, slots: { default: 'x' } }))
      expect(w.classes()).toContain(`ui-button--${variant}`)
      expect(w.attributes('style') ?? '').not.toMatch(/#|rgb|hsl/)
    }
  })
})

describe('UiTag', () => {
  it('renders its text and a tone class', () => {
    const w = track(mount(UiTag, { props: { tone: 'now' }, slots: { default: 'Ahora' } }))

    expect(w.text()).toBe('Ahora')
    expect(w.classes()).toContain('ui-tag--now')
  })
})

describe('UiField', () => {
  it('ties the label to the input and forwards v-model', async () => {
    const w = track(mount(UiField, { props: { label: 'Tema', modelValue: '' } }))

    const input = w.find('input')
    expect(w.find('label').attributes('for')).toBe(input.attributes('id'))
    await input.setValue('Fracciones')
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['Fracciones'])
  })

  it('shows the error as an alert linked to the input', () => {
    const w = track(mount(UiField, { props: { label: 'Tema', modelValue: '', error: 'Escribe un tema' } }))

    const alert = w.find('[role="alert"]')
    expect(alert.text()).toBe('Escribe un tema')
    expect(w.find('input').attributes('aria-invalid')).toBe('true')
    expect(w.find('input').attributes('aria-describedby')).toContain(alert.attributes('id'))
  })

  it('has no error markup without an error', () => {
    const w = track(mount(UiField, { props: { label: 'Tema', modelValue: '' } }))

    expect(w.find('[role="alert"]').exists()).toBe(false)
    expect(w.find('input').attributes('aria-invalid')).toBeUndefined()
  })

  it('passes the input type through (password, email)', () => {
    const w = track(mount(UiField, { props: { label: 'Clave', modelValue: '', type: 'password' } }))

    expect(w.find('input').attributes('type')).toBe('password')
  })
})

describe('UiNotice', () => {
  it('uses an alert role for errors and warnings, and a status role for information', () => {
    expect(track(mount(UiNotice, { props: { tone: 'error' }, slots: { default: 'x' } })).attributes('role')).toBe('alert')
    expect(track(mount(UiNotice, { props: { tone: 'warning' }, slots: { default: 'x' } })).attributes('role')).toBe('alert')
    expect(track(mount(UiNotice, { props: { tone: 'info' }, slots: { default: 'x' } })).attributes('role')).toBe('status')
  })

  it('shows its content as text', () => {
    const w = track(mount(UiNotice, { props: { tone: 'info' }, slots: { default: () => '<b>hola</b>' } }))

    expect(w.text()).toBe('<b>hola</b>')
  })
})

describe('UiProgress', () => {
  it('is a progress bar with its value and a text alternative', () => {
    const w = track(mount(UiProgress, { props: { value: 38, label: 'Historia antigua' } }))
    const bar = w.find('[role="progressbar"]')

    expect(bar.attributes('aria-valuenow')).toBe('38')
    expect(bar.attributes('aria-valuemin')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('100')
    expect(bar.attributes('aria-label')).toBe('Historia antigua')
  })

  it('clamps values outside 0 to 100', () => {
    expect(track(mount(UiProgress, { props: { value: 250, label: 'x' } })).find('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    expect(track(mount(UiProgress, { props: { value: -4, label: 'x' } })).find('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
  })
})

// AC-16: carga, vacío y error homogéneos y sin códigos del backend
describe('UiState', () => {
  it('announces loading politely', () => {
    const w = track(mount(UiState, { props: { state: 'loading' } }))

    expect(w.attributes('role')).toBe('status')
  })

  it('shows an empty message with an optional action slot', () => {
    const w = track(mount(UiState, { props: { state: 'empty', message: 'Aún no tienes cursos' }, slots: { action: '<a href="/nuevo-curso">Crear</a>' } }))

    expect(w.text()).toContain('Aún no tienes cursos')
    expect(w.find('a').exists()).toBe(true)
  })

  it('shows the error as an alert with the message it is given, never a backend code', () => {
    const w = track(mount(UiState, { props: { state: 'error', message: 'No se pudo cargar', code: 'COURSE_BUSY' } }))

    expect(w.attributes('role')).toBe('alert')
    expect(w.text()).toContain('No se pudo cargar')
    expect(w.text()).not.toContain('COURSE_BUSY')
  })
})

describe('UiTabs', () => {
  const tabs = [{ id: 'outline', label: 'Temario' }, { id: 'share', label: 'Compartir' }]
  const slots = { outline: '<p data-testid="panel-outline">A</p>', share: '<p data-testid="panel-share">B</p>' }

  it('exposes a tablist with one selected tab and only its panel', () => {
    const w = track(mount(UiTabs, { props: { tabs, modelValue: 'outline' }, slots, attachTo: document.body }))

    expect(w.find('[role="tablist"]').exists()).toBe(true)
    const triggers = w.findAll('[role="tab"]')
    expect(triggers.map((t) => t.text())).toEqual(['Temario', 'Compartir'])
    expect(triggers[0].attributes('aria-selected')).toBe('true')
    expect(triggers[1].attributes('aria-selected')).toBe('false')
    expect(w.find('[data-testid="panel-outline"]').exists()).toBe(true)
    expect(w.find('[data-testid="panel-share"]').exists()).toBe(false)
  })

  it('asks to switch when another tab is activated', async () => {
    const w = track(mount(UiTabs, { props: { tabs, modelValue: 'outline' }, slots, attachTo: document.body }))

    await w.findAll('[role="tab"]')[1].trigger('mousedown', { button: 0 })

    expect(w.emitted('update:modelValue')?.[0]).toEqual(['share'])
  })
})

describe('UiDialog', () => {
  const open = (props: Record<string, unknown> = {}) =>
    track(mount(UiDialog, {
      props: { modelValue: true, title: 'Quitar tema', description: 'Se borrará su progreso.', confirmLabel: 'Quitar', tone: 'danger', ...props },
      attachTo: document.body,
    }))
  const q = (sel: string) => document.body.querySelector(sel) as HTMLElement | null

  it('is a modal dialog named by its title and described by its text', async () => {
    open()
    await flushPromises()

    const dialog = q('[role="dialog"]')!
    expect(dialog).not.toBeNull()
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.textContent).toBe('Quitar tema')
    expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.textContent).toContain('Se borrará su progreso.')
  })

  it('renders nothing while closed', async () => {
    open({ modelValue: false })
    await flushPromises()

    expect(q('[role="dialog"]')).toBeNull()
  })

  it('emits confirm and closes when the confirm button is used', async () => {
    const w = open()
    await flushPromises()

    q('[data-testid="dialog-confirm"]')!.click()
    await flushPromises()

    expect(w.emitted('confirm')).toHaveLength(1)
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })

  it('closes without confirming on cancel and on Escape', async () => {
    const w = open()
    await flushPromises()

    q('[data-testid="dialog-cancel"]')!.click()
    await flushPromises()
    expect(w.emitted('confirm')).toBeUndefined()
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([false])

    const w2 = open()
    await flushPromises()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(w2.emitted('confirm')).toBeUndefined()
    expect(w2.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })

  it('shows the title and description as text', async () => {
    open({ title: '<img src=x onerror=alert(1)>' })
    await flushPromises()

    expect(q('[role="dialog"] img')).toBeNull()
  })
})
