import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    api: {
      startIntake: vi.fn(), createCourse: vi.fn(), listMaterial: vi.fn(), getIllustrationOffer: vi.fn(), getCourse: vi.fn(), getPiece: vi.fn(), getStudy: vi.fn(),
      getTutorThread: vi.fn(), listShareCodes: vi.fn(), regenerateTopicImage: vi.fn(), removeTopicImage: vi.fn(), me: vi.fn(), logout: vi.fn(),
    },
  }
})
import { api } from '@/api/client'
import CourseMap from '@/map/CourseMap.vue'
import CourseEditView from '@/views/CourseEditView.vue'
import NewCourseView from '@/views/NewCourseView.vue'
import PieceView from '@/views/PieceView.vue'
import { adult, byId, minor, mountApp } from '../helpers'
import { outline, study } from '../map/fixtures'

type Mounted = Awaited<ReturnType<typeof mountApp>>

const q1 = { id: 'q1', text: '¿En qué quieres centrarte?', options: [{ id: 'o1', label: 'Gramática', isDefault: true }, { id: 'auto', label: 'Como tú veas', isDefault: false }] }
const done = { status: 'DONE', alt: 'Ilustración generada por IA sobre «Mesopotamia»' }

afterEach(() => {
  document.body.innerHTML = ''
})

async function toSummary(m: Mounted) {
  await byId(m.wrapper, 'topic-input').setValue('Los volcanes')
  await byId(m.wrapper, 'topic-next').trigger('click')
  await flushPromises()
  await byId(m.wrapper, 'material-no').trigger('click')
  await byId(m.wrapper, 'material-next').trigger('click')
  await flushPromises()
  await byId(m.wrapper, 'questions-next').trigger('click')
  await flushPromises()
}

// F12 AC-3: el interruptor en el último paso del asistente
describe('Asistente · ilustraciones', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listMaterial).mockResolvedValue({ own: [], minors: [] } as never)
    vi.mocked(api.startIntake).mockResolvedValue({ intakeId: 'i1', questions: [q1] } as never)
    vi.mocked(api.createCourse).mockResolvedValue({ id: 'c1', title: 'Volcanes', status: 'PENDING_GENERATION' } as never)
    vi.mocked(api.getIllustrationOffer).mockResolvedValue({ available: true, pricePerImageUsd: 0.05, maxTopics: 12 })
  })

  it('offers the switch, off by default, with the estimated cost in the summary step', async () => {
    const m = await mountApp(NewCourseView, { user: adult, route: '/nuevo-curso' })
    await toSummary(m)

    expect(byId(m.wrapper, 'illustration-toggle').exists()).toBe(true)
    expect((byId(m.wrapper, 'summary').find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false)
    expect(byId(m.wrapper, 'illustration-cost').text()).toContain('0,05')
  })

  it('creates the course without illustrations unless the switch is on, sending exactly the same call as before', async () => {
    const m = await mountApp(NewCourseView, { user: adult, route: '/nuevo-curso' })
    await toSummary(m)

    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()

    expect(api.createCourse).toHaveBeenCalledWith('i1', expect.any(Array))
  })

  it('creates the course with illustrations when the user turns the switch on', async () => {
    const m = await mountApp(NewCourseView, { user: adult, route: '/nuevo-curso' })
    await toSummary(m)

    await byId(m.wrapper, 'summary').find('input[type="checkbox"]').setValue(true)
    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()

    expect(api.createCourse).toHaveBeenCalledWith('i1', expect.any(Array), true)
  })

  it('does not show the switch when the server does not offer it (no provider, or a minor)', async () => {
    vi.mocked(api.getIllustrationOffer).mockResolvedValue({ available: false, pricePerImageUsd: 0, maxTopics: 12 })
    const m = await mountApp(NewCourseView, { user: minor, route: '/nuevo-curso' })
    await toSummary(m)

    expect(byId(m.wrapper, 'illustration-toggle').exists()).toBe(false)
  })

  it('works as before if the offer cannot be loaded: no switch and the course is created normally', async () => {
    vi.mocked(api.getIllustrationOffer).mockRejectedValue(new Error('boom'))
    const m = await mountApp(NewCourseView, { user: adult, route: '/nuevo-curso' })
    await toSummary(m)

    expect(byId(m.wrapper, 'illustration-toggle').exists()).toBe(false)
    await byId(m.wrapper, 'accept').trigger('click')
    await flushPromises()
    expect(api.createCourse).toHaveBeenCalledWith('i1', expect.any(Array))
  })

  it('never offers the switch to a minor even if the offer said yes (the server also refuses)', async () => {
    const m = await mountApp(NewCourseView, { user: minor, route: '/nuevo-curso' })
    await toSummary(m)

    expect(byId(m.wrapper, 'illustration-toggle').exists()).toBe(false)
  })
})

const topicsWithImages = () => {
  const o = outline()
  return o.map((t, i) => ({ ...t, image: i === 0 ? done : i === 1 ? { status: 'PENDING' } : null }))
}

// F12 AC-12: la portada en la tarjeta del tema del mapa
describe('Mapa · portada del tema', () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div/>' } }] })
  const render = (o = topicsWithImages()) => mount(CourseMap, { props: { courseId: 'c1', outline: o as never, study: study(), status: 'READY' }, global: { plugins: [router] } })

  it('shows the illustration in the card of the topic that has one, with its alternative text', () => {
    const w = render()

    const cover = w.find('[data-testid="topic-cover-t1"]')
    expect(cover.exists()).toBe(true)
    expect(cover.find('img').attributes('src')).toBe('/api/courses/c1/topics/t1/image')
    expect(cover.find('img').attributes('alt')).toBe('Ilustración generada por IA sobre «Mesopotamia»')
    expect(w.find('[data-testid="map-topic-t1"]').exists() || w.find('.map-topic').exists()).toBe(true)
  })

  it('shows the plant, with no image, for a pending topic and for a topic without image', () => {
    const w = render()

    expect(w.find('[data-testid="topic-cover-t2"]').find('img').exists()).toBe(false)
    expect(w.find('[data-testid="topic-cover-t2"]').text()).toContain('Preparándose')
    expect(w.find('[data-testid="topic-cover-t3"]').find('img').exists()).toBe(false)
  })

  it('keeps every id and role of the accessible list', () => {
    const w = render()

    expect(w.find('[data-testid="map-list"]').exists()).toBe(true)
    expect(w.find('[data-testid="map-item-l3"]').attributes('aria-current')).toBe('step')
  })

  it('draws a course without any illustration exactly as before', () => {
    const w = render(outline() as never)

    expect(w.findAll('.map-topic img')).toHaveLength(0)
  })
})

// F12 AC-12: la cabecera de la lección
describe('Lección · portada', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getTutorThread).mockResolvedValue({ messages: [], remaining: 30 } as never)
  })

  const lesson = (over: object = {}) => ({ id: 'p1', kind: 'LESSON', status: 'DONE', title: 'El Nilo', markdown: '# Hola', sources: [], topicId: 't1', ...over })

  it('shows the illustration of its topic in the header of the lesson, with the AI label', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(lesson({ image: done }) as never)

    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    const cover = byId(m.wrapper, 'piece-cover')
    expect(cover.find('img').attributes('src')).toBe('/api/courses/c1/topics/t1/image')
    expect(cover.text()).toContain('Ilustración generada por IA')
  })

  it('shows no cover when the topic has no illustration', async () => {
    vi.mocked(api.getPiece).mockResolvedValue(lesson() as never)

    const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })

    expect(byId(m.wrapper, 'piece-cover').exists()).toBe(false)
  })

  it('does not put a cover on exercises or exams', async () => {
    for (const kind of ['EXERCISES', 'EXAM']) {
      vi.mocked(api.getPiece).mockResolvedValue(lesson({ kind, image: done, exercises: [] }) as never)
      const m = await mountApp(PieceView, { user: adult, route: '/cursos/c1/piezas/p1' })
      expect(byId(m.wrapper, 'piece-cover').exists(), kind).toBe(false)
    }
  })
})

// F12 AC-13: quitar y volver a generar
describe('Edición · ilustraciones', () => {
  const inBody = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  const detail = (over: object = {}) => ({
    id: 'c1', title: 'Historia', status: 'READY', version: 4, createdAt: '2026-10-25T10:00:00Z', sheet: {}, documents: [], illustrations: true, outline: topicsWithImages(), ...over,
  })

  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail() as never)
    vi.mocked(api.listShareCodes).mockResolvedValue([] as never)
    vi.mocked(api.regenerateTopicImage).mockResolvedValue(detail() as never)
    vi.mocked(api.removeTopicImage).mockResolvedValue(detail() as never)
  })

  it('offers to remove and to regenerate the illustration of a topic that has one, and only regenerate when it failed', async () => {
    const o = topicsWithImages()
    o[1] = { ...o[1], image: { status: 'FAILED' } } as never
    vi.mocked(api.getCourse).mockResolvedValue(detail({ outline: o }) as never)

    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    expect(byId(m.wrapper, 'edit-image-remove-t1').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-image-regenerate-t1').exists()).toBe(true)
    expect(byId(m.wrapper, 'edit-image-remove-t2').exists()).toBe(false)
    expect(byId(m.wrapper, 'edit-image-regenerate-t2').exists()).toBe(true)
  })

  it('shows nothing about illustrations in a course that has none', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ illustrations: false, outline: outline() }) as never)

    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    expect(byId(m.wrapper, 'edit-image-regenerate-t1').exists()).toBe(false)
    expect(byId(m.wrapper, 'edit-image-remove-t1').exists()).toBe(false)
  })

  it('asks for confirmation, saying it counts for the spending, before regenerating', async () => {
    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    await byId(m.wrapper, 'edit-image-regenerate-t1').trigger('click')
    await flushPromises()

    expect(api.regenerateTopicImage).not.toHaveBeenCalled()
    expect((inBody('edit-confirm')?.textContent ?? '').toLowerCase()).toContain('gasto')
    inBody('edit-confirm-yes')?.click()
    await flushPromises()
    expect(api.regenerateTopicImage).toHaveBeenCalledWith('c1', 't1')
  })

  it('asks for confirmation before removing, and removes only when confirmed', async () => {
    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    await byId(m.wrapper, 'edit-image-remove-t1').trigger('click')
    await flushPromises()
    inBody('edit-confirm-no')?.click()
    await flushPromises()
    expect(api.removeTopicImage).not.toHaveBeenCalled()

    await byId(m.wrapper, 'edit-image-remove-t1').trigger('click')
    await flushPromises()
    inBody('edit-confirm-yes')?.click()
    await flushPromises()
    expect(api.removeTopicImage).toHaveBeenCalledWith('c1', 't1')
  })

  it('disables the image actions while the course is being generated', async () => {
    vi.mocked(api.getCourse).mockResolvedValue(detail({ status: 'GENERATING' }) as never)

    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    expect(byId(m.wrapper, 'edit-image-regenerate-t1').attributes('disabled')).toBeDefined()
    expect(byId(m.wrapper, 'edit-image-remove-t1').attributes('disabled')).toBeDefined()
  })

  it('explains a limit error in plain words without showing the code', async () => {
    const { ApiError } = await import('@/api/client')
    vi.mocked(api.regenerateTopicImage).mockRejectedValue(new ApiError(429, 'REGENERATION_LIMIT'))
    const m = await mountApp(CourseEditView, { user: adult, route: '/cursos/c1/editar' })

    await byId(m.wrapper, 'edit-image-regenerate-t1').trigger('click')
    await flushPromises()
    inBody('edit-confirm-yes')?.click()
    await flushPromises()

    const error = byId(m.wrapper, 'edit-error')
    expect(error.exists()).toBe(true)
    expect(error.text()).not.toContain('REGENERATION_LIMIT')
  })
})
