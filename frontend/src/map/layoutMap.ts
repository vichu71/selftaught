import type { OutlineTopic, PieceRef, StudyOverview } from '@/api/client'

/**
 * Colocación del mapa de un curso (F10, ADR-68). Función pura: del temario y del estudio salen las paradas, los tramos y las zonas.
 * No sabe de Vue ni del DOM; el componente solo las pinta.
 */
export type StationState = 'done' | 'now' | 'todo'

export interface Station {
  /** Id de la pieza (lección o examen del tema). */
  id: string
  kind: 'lesson' | 'exam'
  topicId: string
  label: string
  state: StationState
  queued: boolean
  failed: boolean
  outdated: boolean
  x: number
  y: number
  /** Los ejercicios de la lección cuelgan de ella (no son parada). */
  exercises: { id: string; route: string | null } | null
  /** Solo las piezas listas se pueden abrir. */
  route: string | null
}

export interface Segment { from: string; to: string; done: boolean }

export interface Zone {
  topicId: string
  title: string
  y: number
  height: number
  collapsed: boolean
  /** Paradas del tema (lecciones + examen) y cuántas están hechas, también si el tema está plegado. */
  total: number
  done: number
}

export interface MapLayout {
  stations: Station[]
  segments: Segment[]
  zones: Zone[]
  width: number
  height: number
  nowId: string | null
}

export interface LayoutOptions {
  courseId: string
  mode: 'wide' | 'narrow'
  width?: number
  collapsed?: Set<string>
}

export interface NextStop {
  pieceId: string
  kind: 'lesson' | 'exam'
  label: string
  topicTitle: string
  route: string
}

const PAD = 60
const STEP = 110
const ROW_H = 110
const ROW_NARROW = 72
const ZONE_HEAD = 44
const ZONE_GAP = 22
const FOLD_LIMIT = 40

interface Entry {
  piece: PieceRef
  kind: 'lesson' | 'exam'
  topicId: string
  label: string
  done: boolean
  exercises: PieceRef | null
}

interface TopicEntries { topic: OutlineTopic; entries: Entry[] }

function collect(outline: OutlineTopic[], study: StudyOverview | null): TopicEntries[] {
  const byTopic = new Map((study?.topics ?? []).map((t) => [t.id, t]))
  return outline.map((topic) => {
    const s = byTopic.get(topic.id)
    const doneLessons = new Set((s?.lessons ?? []).filter((l) => l.done).map((l) => l.pieceId))
    const entries: Entry[] = []
    for (const chapter of topic.chapters) {
      if (chapter.lesson) entries.push({ piece: chapter.lesson, kind: 'lesson', topicId: topic.id, label: chapter.title, done: doneLessons.has(chapter.lesson.id), exercises: chapter.exercises ?? null })
    }
    if (topic.exam) entries.push({ piece: topic.exam, kind: 'exam', topicId: topic.id, label: `Examen: ${topic.title}`, done: s?.exam?.passed === true, exercises: null })
    return { topic, entries }
  })
}

/** El primer elemento por hacer y listo; el examen solo cuando todas las lecciones de su tema están hechas. */
function findNow(groups: TopicEntries[]): string | null {
  for (const { entries } of groups) {
    const lessonsDone = entries.filter((e) => e.kind === 'lesson').every((e) => e.done)
    for (const e of entries) {
      if (e.done || e.piece.status !== 'DONE') continue
      if (e.kind === 'exam' && !lessonsDone) continue
      return e.piece.id
    }
  }
  return null
}

function stateOf(e: Entry, nowId: string | null): StationState {
  return e.done ? 'done' : e.piece.id === nowId ? 'now' : 'todo'
}

export function layoutMap(outline: OutlineTopic[], study: StudyOverview | null, opts: LayoutOptions): MapLayout {
  const groups = collect(outline, study)
  const nowId = findNow(groups)
  const narrow = opts.mode === 'narrow'
  const width = opts.width ?? (narrow ? 360 : 1100)
  const perRow = narrow ? 1 : Math.max(1, Math.floor((width - 2 * PAD) / STEP) + 1)
  const collapsed = opts.collapsed ?? new Set<string>()

  const stations: Station[] = []
  const zones: Zone[] = []
  let top = 0

  for (const { topic, entries } of groups) {
    const fold = collapsed.has(topic.id)
    const rows = fold ? 0 : narrow ? entries.length : Math.ceil(entries.length / perRow)
    const rowHeight = narrow ? ROW_NARROW : ROW_H
    const height = ZONE_HEAD + (fold ? 8 : rows * rowHeight)

    if (!fold) {
      entries.forEach((e, i) => {
        const row = narrow ? i : Math.floor(i / perRow)
        const idx = narrow ? 0 : i % perRow
        const col = !narrow && row % 2 === 1 ? perRow - 1 - idx : idx
        const x = narrow ? PAD + (i % 2) * 40 : PAD + col * STEP
        const y = top + ZONE_HEAD + row * rowHeight + rowHeight / 2
        stations.push({
          id: e.piece.id,
          kind: e.kind,
          topicId: topic.id,
          label: e.label,
          state: stateOf(e, nowId),
          queued: e.piece.status === 'PENDING',
          failed: e.piece.status === 'FAILED',
          outdated: e.piece.outdated === true,
          x,
          y,
          exercises: e.exercises
            ? { id: e.exercises.id, route: e.exercises.status === 'DONE' ? `/cursos/${opts.courseId}/piezas/${e.exercises.id}` : null }
            : null,
          route: e.piece.status === 'DONE' ? `/cursos/${opts.courseId}/piezas/${e.piece.id}` : null,
        })
      })
    }

    zones.push({
      topicId: topic.id,
      title: topic.title,
      y: top,
      height,
      collapsed: fold,
      total: entries.length,
      done: entries.filter((e) => e.done).length,
    })
    top += height + ZONE_GAP
  }

  const segments: Segment[] = []
  for (let i = 0; i < stations.length - 1; i++) {
    segments.push({ from: stations[i].id, to: stations[i + 1].id, done: stations[i].state === 'done' })
  }

  return { stations, segments, zones, width, height: Math.max(0, top - ZONE_GAP) + PAD / 2, nowId }
}

/** Con muchas paradas solo se deja abierto el tema donde toca estudiar. */
export function defaultCollapsed(outline: OutlineTopic[], study: StudyOverview | null): Set<string> {
  const groups = collect(outline, study)
  const total = groups.reduce((n, g) => n + g.entries.length, 0)
  if (total <= FOLD_LIMIT) return new Set()
  const nowId = findNow(groups)
  const open = groups.find((g) => g.entries.some((e) => e.piece.id === nowId))?.topic.id ?? groups[0]?.topic.id
  return new Set(groups.map((g) => g.topic.id).filter((id) => id !== open))
}

/** «Sigue donde lo dejaste»: la parada «ahora» del curso, o nada. */
export function nextStop(courseId: string, outline: OutlineTopic[], study: StudyOverview | null): NextStop | null {
  const groups = collect(outline, study)
  const nowId = findNow(groups)
  if (!nowId) return null
  for (const { topic, entries } of groups) {
    const e = entries.find((x) => x.piece.id === nowId)
    if (e) return { pieceId: e.piece.id, kind: e.kind, label: e.label, topicTitle: topic.title, route: `/cursos/${courseId}/piezas/${e.piece.id}` }
  }
  return null
}
