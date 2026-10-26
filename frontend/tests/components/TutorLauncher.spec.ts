import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getPiece: vi.fn(), getTutorThread: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { api } from '@/api/client'
import TutorLauncher from '@/components/TutorLauncher.vue'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, mountApp } from '../helpers'

const mounted: VueWrapper[] = []
afterEach(() => {
  mounted.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
})

const open = () => {
  const w = mount(TutorLauncher, { slots: { default: '<textarea data-testid="ask"></textarea><p data-testid="inside">dentro</p>' }, attachTo: document.body })
  mounted.push(w)
  return w
}

// El tutor es un botón flotante que abre un panel junto a la lección, sin tener que ir al final de la página
describe('TutorLauncher', () => {
  it('shows a floating button and keeps the panel closed at first', () => {
    const w = open()

    const button = byId(w, 'tutor-open')
    expect(button.exists()).toBe(true)
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(button.text()).toContain('tutor')
    expect(byId(w, 'tutor-panel').isVisible()).toBe(false)
  })

  it('opens the panel with the button, marks it as expanded and moves the focus to the question', async () => {
    const w = open()

    await byId(w, 'tutor-open').trigger('click')
    await flushPromises()

    expect(byId(w, 'tutor-open').attributes('aria-expanded')).toBe('true')
    expect(byId(w, 'tutor-panel').isVisible()).toBe(true)
    expect(document.activeElement).toBe(byId(w, 'ask').element)
  })

  it('closes with the button again, with the close control and with Escape, returning the focus to the button', async () => {
    const w = open()

    await byId(w, 'tutor-open').trigger('click')
    await byId(w, 'tutor-open').trigger('click')
    expect(byId(w, 'tutor-panel').isVisible()).toBe(false)

    await byId(w, 'tutor-open').trigger('click')
    await byId(w, 'tutor-close').trigger('click')
    expect(byId(w, 'tutor-panel').isVisible()).toBe(false)
    expect(document.activeElement).toBe(byId(w, 'tutor-open').element)

    await byId(w, 'tutor-open').trigger('click')
    await byId(w, 'ask').trigger('keydown', { key: 'Escape' })
    expect(byId(w, 'tutor-panel').isVisible()).toBe(false)
    expect(document.activeElement).toBe(byId(w, 'tutor-open').element)
  })

  it('keeps its content mounted while closed, so what the student wrote is not lost', async () => {
    const w = open()

    expect(byId(w, 'inside').exists()).toBe(true)
    await byId(w, 'ask').setValue('mi duda')
    await byId(w, 'tutor-open').trigger('click')
    await byId(w, 'tutor-open').trigger('click')

    expect((byId(w, 'ask').element as HTMLTextAreaElement).value).toBe('mi duda')
  })

  it('names the panel for assistive technology and ties the button to it', () => {
    const w = open()

    const panel = byId(w, 'tutor-panel')
    expect(panel.attributes('aria-label')).toBeTruthy()
    expect(byId(w, 'tutor-open').attributes('aria-controls')).toBe(panel.attributes('id'))
    expect(byId(w, 'tutor-close').attributes('aria-label')).toBeTruthy()
  })
})

describe('PieceView · tutor flotante', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getTutorThread).mockResolvedValue({ messages: [], remaining: 30 } as never)
  })

  const piece = (kind: string) => ({ id: 'p1', kind, status: 'DONE', title: 'El Nilo', markdown: '# Hola', sources: [], exercises: [] })

  it('shows the launcher in a lesson, with the tutor chat inside the panel', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(piece('LESSON') as never)

    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'tutor-open').exists()).toBe(true)
    expect(byId(m.wrapper, 'tutor-panel').find('[data-testid="tutor-ai-notice"]').exists()).toBe(true)
    expect(byId(m.wrapper, 'tutor-panel').isVisible()).toBe(false)
  })

  it('has no tutor in exercises or exams (the tutor never solves them)', async () => {
    for (const kind of ['EXERCISES', 'EXAM']) {
      vi.mocked(api.getPiece).mockResolvedValue(piece(kind) as never)
      const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

      expect(byId(m.wrapper, 'tutor-open').exists(), kind).toBe(false)
    }
  })
})
