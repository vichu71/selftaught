import { describe, expect, it } from 'vitest'
import { defaultCollapsed, layoutMap, nextStop } from '@/map/layoutMap'
import { bigOutline, outline, piece, study } from './fixtures'

const wide = { courseId: 'c1', mode: 'wide' as const, width: 1100 }
const narrow = { courseId: 'c1', mode: 'narrow' as const, width: 360 }

// F10 AC-4: una parada por lección y por examen de tema, en el orden del curso; los ejercicios no son parada
describe('layoutMap · paradas', () => {
  it('creates one station per lesson and per topic exam, in course order, and none for exercises', () => {
    const map = layoutMap(outline(), study(), wide)

    expect(map.stations.map((s) => s.id)).toEqual(['l1', 'l2', 'x1', 'l3', 'l4', 'x2', 'l5', 'x3'])
    expect(map.stations.filter((s) => s.kind === 'lesson')).toHaveLength(5)
    expect(map.stations.filter((s) => s.kind === 'exam')).toHaveLength(3)
    expect(map.stations.some((s) => s.id.startsWith('e'))).toBe(false)
  })

  it('skips chapters without a lesson and topics without exam', () => {
    const o = outline()
    o[0].chapters[1].lesson = null
    o[2].exam = null

    const ids = layoutMap(o, study(), wide).stations.map((s) => s.id)

    expect(ids).not.toContain('l2')
    expect(ids).not.toContain('x3')
  })

  it('marks done lessons and passed exams, and exactly one "now": the first ready lesson not done', () => {
    const map = layoutMap(outline(), study(), wide)
    const state = Object.fromEntries(map.stations.map((s) => [s.id, s.state]))

    expect(state).toMatchObject({ l1: 'done', l2: 'done', x1: 'done', l3: 'now', l4: 'todo', x2: 'todo', l5: 'todo', x3: 'todo' })
    expect(map.nowId).toBe('l3')
    expect(map.stations.filter((s) => s.state === 'now')).toHaveLength(1)
  })

  it('does not put "now" on an exam whose topic still has lessons to do', () => {
    const s = study()
    s.topics[1].lessons[0].done = true // l3 hecha; l4 sigue en cola: el examen x2 aún no toca
    const map = layoutMap(outline(), s, wide)

    expect(map.stations.find((x) => x.id === 'x2')?.state).toBe('todo')
  })

  it('puts "now" on the topic exam when all its lessons are done and it is ready and not passed', () => {
    const o = outline()
    o[1].chapters[1].lesson = piece('l4')
    o[1].exam = piece('x2')
    const s = study()
    s.topics[1].lessons = [{ pieceId: 'l3', status: 'DONE', done: true }, { pieceId: 'l4', status: 'DONE', done: true }]

    expect(layoutMap(o, s, wide).nowId).toBe('x2')
  })

  it('has no "now" when nothing is ready yet (course still being generated) and when everything is done', () => {
    const queued = outline().map((t) => ({
      ...t,
      chapters: t.chapters.map((c) => ({ ...c, lesson: piece(c.lesson!.id, 'PENDING') })),
      exam: piece(t.exam!.id, 'PENDING'),
    }))
    expect(layoutMap(queued, null, wide).nowId).toBeNull()

    const all = study()
    all.topics.forEach((t) => {
      t.lessons.forEach((l) => (l.done = true))
      if (t.exam) t.exam.passed = true
    })
    const done = outline().map((t) => ({
      ...t,
      chapters: t.chapters.map((c) => ({ ...c, lesson: piece(c.lesson!.id) })),
      exam: piece(t.exam!.id),
    }))
    const map = layoutMap(done, all, wide)
    expect(map.nowId).toBeNull()
    expect(map.stations.every((s) => s.state === 'done')).toBe(true)
  })

  it('without study data nothing is done and the first ready lesson is "now"', () => {
    const map = layoutMap(outline(), null, wide)

    expect(map.stations.filter((s) => s.state === 'done')).toHaveLength(0)
    expect(map.nowId).toBe('l1')
  })

  // AC-5: marcas independientes del estado
  it('flags queued, failed and outdated pieces apart from their state', () => {
    const o = outline()
    o[1].chapters[1].lesson = piece('l4', 'FAILED')
    o[0].chapters[0].lesson = piece('l1', 'DONE', true)
    const byId = Object.fromEntries(layoutMap(o, study(), wide).stations.map((s) => [s.id, s]))

    expect(byId.l4).toMatchObject({ failed: true, queued: false })
    expect(byId.l5).toMatchObject({ queued: true, failed: false })
    expect(byId.l1.outdated).toBe(true)
    expect(byId.l2.outdated).toBe(false)
  })

  it('only gives a route to ready pieces', () => {
    const byId = Object.fromEntries(layoutMap(outline(), study(), wide).stations.map((s) => [s.id, s]))

    expect(byId.l3.route).toBe('/cursos/c1/piezas/l3')
    expect(byId.x1.route).toBe('/cursos/c1/piezas/x1')
    expect(byId.l4.route).toBeNull()
  })

  it('keeps the labels as plain text, hostile or not', () => {
    const o = outline()
    o[0].chapters[0].title = '<img src=x onerror=alert(1)>'

    const station = layoutMap(o, study(), wide).stations.find((s) => s.id === 'l1')

    expect(station?.label).toBe('<img src=x onerror=alert(1)>')
  })
})

// AC-4, AC-14: colocación
describe('layoutMap · colocación', () => {
  it('connects consecutive stations; a segment is done when the station it leaves is done', () => {
    const map = layoutMap(outline(), study(), wide)

    expect(map.segments).toHaveLength(map.stations.length - 1)
    expect(map.segments[0]).toMatchObject({ from: 'l1', to: 'l2', done: true })
    expect(map.segments.find((s) => s.from === 'l3')?.done).toBe(false)
  })

  it('wide mode: one zone per topic, stacked, every station inside the width and no two on the same spot', () => {
    const map = layoutMap(outline(), study(), wide)

    expect(map.zones.map((z) => z.topicId)).toEqual(['t1', 't2', 't3'])
    expect(map.zones[1].y).toBeGreaterThan(map.zones[0].y)
    for (const s of map.stations) {
      expect(s.x).toBeGreaterThanOrEqual(0)
      expect(s.x).toBeLessThanOrEqual(map.width)
      expect(s.y).toBeGreaterThanOrEqual(0)
      expect(s.y).toBeLessThanOrEqual(map.height)
    }
    expect(new Set(map.stations.map((s) => `${s.x},${s.y}`)).size).toBe(map.stations.length)
  })

  it('narrow mode: a single column that never exceeds the width', () => {
    const map = layoutMap(outline(), study(), narrow)

    expect(map.width).toBeLessThanOrEqual(360)
    expect(new Set(map.stations.map((s) => s.x)).size).toBeLessThanOrEqual(3) // columna con pequeños quiebros
    const ys = map.stations.map((s) => s.y)
    expect([...ys].sort((a, b) => a - b)).toEqual(ys) // en orden, de arriba abajo
  })

  // Caso borde: el máximo de piezas (150)
  it('a course with 150 pieces stays inside the width and only grows in height', () => {
    const o = bigOutline(10, 7) // 10 temas · 7 lecciones + 1 examen = 80 paradas (150 piezas con ejercicios)
    const map = layoutMap(o, null, wide)

    expect(map.stations).toHaveLength(80)
    expect(Math.max(...map.stations.map((s) => s.x))).toBeLessThanOrEqual(map.width)
    expect(new Set(map.stations.map((s) => `${s.x},${s.y}`)).size).toBe(80)
    expect(map.height).toBeGreaterThan(layoutMap(outline(), null, wide).height)
  })

  it('handles one topic with one chapter, and an empty outline', () => {
    const one = bigOutline(1, 1)
    expect(layoutMap(one, null, wide).stations).toHaveLength(2)
    const none = layoutMap([], null, wide)
    expect(none.stations).toEqual([])
    expect(none.nowId).toBeNull()
  })

  it('collapsed topics keep their zone but contribute no stations', () => {
    const map = layoutMap(outline(), study(), { ...wide, collapsed: new Set(['t1']) })

    expect(map.zones.find((z) => z.topicId === 't1')).toMatchObject({ collapsed: true, total: 3, done: 3 })
    expect(map.stations.map((s) => s.id)).not.toContain('l1')
    expect(map.stations.map((s) => s.id)).toContain('l3')
  })

  it('is a pure function: same input, same output, input untouched', () => {
    const o = outline()
    const copy = JSON.stringify(o)

    expect(layoutMap(o, study(), wide)).toEqual(layoutMap(o, study(), wide))
    expect(JSON.stringify(o)).toBe(copy)
  })
})

describe('defaultCollapsed', () => {
  it('collapses nothing for a small course', () => {
    expect(defaultCollapsed(outline(), study()).size).toBe(0)
  })

  it('for a course of more than 40 stations keeps open only the topic with "now"', () => {
    const o = bigOutline(10, 7)
    const s = {
      lessonsDone: 0, lessonsTotal: 70, examsPassed: 0, examsTotal: 10,
      topics: o.map((t, i) => ({
        id: t.id, percent: 0,
        lessons: t.chapters.map((c) => ({ pieceId: c.lesson!.id, status: 'DONE' as const, done: i < 3 })),
        exam: { pieceId: t.exam!.id, status: 'DONE' as const, attempts: 1, passed: i < 3 },
      })),
    }

    const collapsed = defaultCollapsed(o, s)

    expect(collapsed.has('T3')).toBe(false) // el cuarto tema tiene la primera lección sin hacer
    expect(collapsed.size).toBe(9)
  })
})

// AC-7: «sigue donde lo dejaste»
describe('nextStop', () => {
  it('returns the "now" lesson with its topic and its route', () => {
    expect(nextStop('c1', outline(), study())).toEqual({
      pieceId: 'l3', kind: 'lesson', label: 'El Nilo y su gente', topicTitle: 'Egipto', route: '/cursos/c1/piezas/l3',
    })
  })

  it('returns null when there is nothing to continue', () => {
    expect(nextStop('c1', [], null)).toBeNull()
  })
})
