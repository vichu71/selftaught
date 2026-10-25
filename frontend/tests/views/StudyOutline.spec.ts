import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { getCourse: vi.fn(), getStudy: vi.fn(), getStudyProgress: vi.fn(), listCourses: vi.fn(), resumeCourse: vi.fn(), me: vi.fn(), logout: vi.fn() } }
})
import { ApiError, api } from '@/api/client'
import CoursesPanel from '@/components/CoursesPanel.vue'
import CourseStatusView from '@/views/CourseStatusView.vue'
import { adult, byId, mountApp } from '../helpers'

const sheet = { schemaVersion: 1, topic: 'Java', focus: 'General', startLevel: 'Desde cero', goal: 'Aprender', topicsCount: 2, examPerTopic: true, documentIds: [] }

const detail = {
  id: 'c1', title: 'Java básico', status: 'GENERATING', createdAt: '2026-10-25T10:00:00Z', sheet, documents: [], generatedByAi: true, progress: { done: 3, total: 6 },
  outline: [{
    id: 't1', title: 'Introducción', summary: 'Qué es Java',
    chapters: [
      { id: 'ch1', title: '¿Qué es?', objective: 'x', lesson: { id: 'p1', status: 'DONE' }, exercises: { id: 'p2', status: 'DONE' } },
      { id: 'ch2', title: 'El JDK', objective: 'y', lesson: { id: 'p3', status: 'PENDING' }, exercises: { id: 'p4', status: 'PENDING' } },
    ],
    exam: { id: 'p5', status: 'DONE' },
  }],
}

const study = {
  percent: 38, lessonsDone: 1, lessonsTotal: 2, examsPassed: 1, examsTotal: 1,
  topics: [{
    id: 't1', percent: 67,
    lessons: [{ pieceId: 'p1', status: 'DONE', done: true }, { pieceId: 'p3', status: 'PENDING', done: false }],
    exam: { pieceId: 'p5', status: 'DONE', attempts: 2, best: 80, last: 60, passed: true },
  }],
}

// AC-1, AC-2 y AC-12: el avance en «Tus cursos» y el estado de estudio de cada lección y de cada examen en el temario
describe('Study state in the course outline', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCourse).mockResolvedValue(detail as never)
    vi.mocked(api.getStudy).mockResolvedValue(study as never)
  })

  it('loads the study state of the course of the route', async () => {
    await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(api.getStudy).toHaveBeenCalledWith('c1')
  })

  it('shows the progress of the whole course', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'study-progress').text()).toContain('38')
  })

  it('marks the lessons that are done and not the others', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'done-p1').exists()).toBe(true)
    expect(byId(m.wrapper, 'done-p3').exists()).toBe(false)
  })

  it('shows the progress of every topic', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'topic-percent-t1').text()).toContain('67')
  })

  it('shows the best mark of the exam and whether the topic is passed', async () => {
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'exam-best-t1').text()).toContain('80')
    expect(byId(m.wrapper, 'exam-best-t1').text().toLowerCase()).toContain('superado')
  })

  it('says the exam has not been taken yet when there are no attempts', async () => {
    vi.mocked(api.getStudy).mockResolvedValue({ ...study, topics: [{ ...study.topics[0], exam: { pieceId: 'p5', status: 'DONE', attempts: 0, passed: false } }] } as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'exam-best-t1').text().toLowerCase()).toContain('sin hacer')
  })

  it('does not break the page if the study state cannot be loaded', async () => {
    vi.mocked(api.getStudy).mockRejectedValue(new ApiError(500))
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'outline').exists()).toBe(true)
    expect(byId(m.wrapper, 'study-progress').exists()).toBe(false)
  })

  it('shows no percentage for a course that has no outline yet', async () => {
    vi.mocked(api.getCourse).mockResolvedValue({ ...detail, status: 'PENDING_GENERATION', outline: [], progress: { done: 0, total: 0 } } as never)
    vi.mocked(api.getStudy).mockResolvedValue({ lessonsDone: 0, lessonsTotal: 0, examsPassed: 0, examsTotal: 0, topics: [] } as never)
    const m = await mountApp(CourseStatusView, { user: adult, route: '/cursos/c1' })

    expect(byId(m.wrapper, 'study-progress').exists()).toBe(false)
  })
})

describe('Progress in «Tus cursos»', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCourses).mockResolvedValue([
      { id: 'c1', title: 'Java', status: 'READY', sheet, documents: [], createdAt: '' },
      { id: 'c2', title: 'Inglés', status: 'PENDING_GENERATION', sheet, documents: [], createdAt: '' },
    ] as never)
    vi.mocked(api.getStudyProgress).mockResolvedValue([{ courseId: 'c1', percent: 38 }, { courseId: 'c2' }] as never)
  })

  it('shows the percentage next to each course that has one', async () => {
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-percent-c1').text()).toContain('38')
    expect(byId(m.wrapper, 'course-percent-c2').exists()).toBe(false)
  })

  it('still lists the courses if the progress cannot be loaded', async () => {
    vi.mocked(api.getStudyProgress).mockRejectedValue(new ApiError(500))
    const m = await mountApp(CoursesPanel, { user: adult })

    expect(byId(m.wrapper, 'course-c1').exists()).toBe(true)
    expect(byId(m.wrapper, 'course-percent-c1').exists()).toBe(false)
  })
})
