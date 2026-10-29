import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getCourse: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { api } from '@/api/client'
import CourseMaterial from '@/components/CourseMaterial.vue'
import CourseStatusView from '@/views/CourseStatusView.vue'
import { adult, byId, mountApp } from '../helpers'

const course = {
  id: 'c1', title: 'Matemáticas de 1º', status: 'READY', createdAt: '2026-10-25T10:00:00Z',
  sheet: { schemaVersion: 1, topic: 'Matemáticas de 1º', focus: 'General', startLevel: 'Desde cero', goal: 'Aprobar', topicsCount: 6, examPerTopic: true, documentIds: [] },
  documents: [{ id: 'd1', name: 'apuntes.pdf' }, { id: 'd2', name: 'resumen.docx' }, { id: 'd3', name: 'notas.txt' }],
}

// F18 AC-4 (D4): «Material de este curso» en lugar de la línea «Documentos: …»
describe('CourseMaterial', () => {
  it('lists every document with an icon by type and its name', () => {
    const wrapper = mount(CourseMaterial, { props: { documents: course.documents } })

    expect(byId(wrapper, 'course-material').text()).toContain('Material de este curso')
    for (const d of course.documents) expect(byId(wrapper, `course-material-item-${d.id}`).text()).toContain(d.name)
    expect(byId(wrapper, 'course-material-item-d1').find('svg').exists()).toBe(true)
  })

  it('draws nothing when the course has no documents', () => {
    const wrapper = mount(CourseMaterial, { props: { documents: [] } })

    expect(byId(wrapper, 'course-material').exists()).toBe(false)
  })

  it('shows names as plain text, also hostile ones, and lets long ones wrap', () => {
    const wrapper = mount(CourseMaterial, { props: { documents: [{ id: 'x', name: '<img src=x onerror=alert(1)>' + 'a'.repeat(200) }] } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })
})

describe('CourseStatusView · material', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(course as never)
  })

  it('shows the material card with the course documents and no longer the old text line', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-material').exists()).toBe(true)
    expect(byId(m.wrapper, 'course-material-item-d2').text()).toContain('resumen.docx')
    expect(m.wrapper.text()).not.toContain('Documentos:')
  })

  it('shows no material card when the course has no documents', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ ...course, documents: [] } as never)

    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'course-material').exists()).toBe(false)
  })
})
