<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { CourseStatus, OutlineTopic, StudyOverview } from '@/api/client'
import MapLegend from './MapLegend.vue'
import MapStation from './MapStation.vue'
import { defaultCollapsed, layoutMap, type Station } from './layoutMap'
import { roadPath } from './roadPath'
import UiTag from '@/ui/UiTag.vue'

const props = defineProps<{ courseId: string; outline: OutlineTopic[]; study?: StudyOverview | null; status?: CourseStatus }>()

const study = computed(() => props.study ?? null)
const narrow = ref(false)
const collapsed = ref<Set<string>>(defaultCollapsed(props.outline, study.value))

let media: MediaQueryList | null = null
const syncMedia = () => { narrow.value = media?.matches === true }
onMounted(() => {
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    media = window.matchMedia('(max-width: 39.99rem)')
    syncMedia()
    media.addEventListener?.('change', syncMedia)
  }
})
onBeforeUnmount(() => media?.removeEventListener?.('change', syncMedia))
watch(() => props.outline.length, () => { collapsed.value = defaultCollapsed(props.outline, study.value) })

const mode = computed(() => (narrow.value ? 'narrow' : 'wide'))
// El dibujo omite los temas plegados; la lista los conserva todos (misma información para teclado y lector de pantalla)
const drawn = computed(() => layoutMap(props.outline, study.value, { courseId: props.courseId, mode: mode.value, collapsed: collapsed.value }))
const full = computed(() => layoutMap(props.outline, study.value, { courseId: props.courseId, mode: mode.value }))

const lessons = computed(() => full.value.stations.filter((s) => s.kind === 'lesson'))
const lessonsDone = computed(() => lessons.value.filter((s) => s.state === 'done').length)
const finished = computed(() => full.value.stations.length > 0 && full.value.stations.every((s) => s.state === 'done'))
const waiting = computed(() => props.status === 'PENDING_GENERATION' || props.status === 'GENERATING')
const summary = computed(() => `Mapa del curso: ${lessonsDone.value} de ${lessons.value.length} lecciones hechas`)

const stationsOf = (topicId: string): Station[] => full.value.stations.filter((s) => s.topicId === topicId)
const topicTitle = (id: string) => props.outline.find((t) => t.id === id)?.title ?? ''

function stateText(s: Station): string {
  if (s.state === 'done') return s.kind === 'exam' ? 'Tema superado' : 'Hecha'
  return s.state === 'now' ? 'Ahora' : 'Por venir'
}

const topicStudy = (id: string) => study.value?.topics.find((t) => t.id === id)

function examNote(topicId: string): string {
  const exam = topicStudy(topicId)?.exam
  if (!exam) return ''
  if (exam.attempts === 0) return 'Sin hacer'
  return exam.passed ? `Mejor nota ${exam.best} % · tema superado` : `Mejor nota ${exam.best} % · repasa y vuelve a intentarlo`
}

function toggle(topicId: string) {
  const next = new Set(collapsed.value)
  if (next.has(topicId)) next.delete(topicId)
  else next.add(topicId)
  collapsed.value = next
}

/**
 * Puntos del camino por las paradas dadas. En la misma fila va recto; al cambiar de fila sale primero hacia un lado (en ancho el
 * nombre cuelga debajo de la parada), baja hasta el hueco entre filas, cruza y vuelve a bajar. `roadPath` redondea las esquinas.
 */
function roadPoints(stops: Station[]): { x: number; y: number }[] {
  const pts: { x: number; y: number }[] = []
  stops.forEach((b, i) => {
    const a = stops[i - 1]
    if (a && a.y !== b.y && a.x !== b.x) {
      const mid = (a.y + b.y) / 2
      const out = narrow.value ? a.x : a.x + 56
      pts.push({ x: out, y: a.y }, { x: out, y: mid }, { x: b.x, y: mid })
    }
    pts.push({ x: b.x, y: b.y })
  })
  return pts
}

const road = computed(() => roadPath(roadPoints(drawn.value.stations)))
// Lo recorrido: desde la primera parada mientras los tramos consecutivos estén hechos
const travelled = computed(() => {
  const segments = drawn.value.segments
  let k = 0
  while (k < segments.length && segments[k].done) k++
  return k === 0 ? '' : roadPath(roadPoints(drawn.value.stations.slice(0, k + 1)))
})
</script>

<template>
  <section class="course-map-wrap">
    <svg
      class="course-map"
      data-testid="course-map"
      role="img"
      :aria-label="summary"
      :viewBox="`0 0 ${drawn.width} ${Math.max(drawn.height, 120)}`"
      xmlns="http://www.w3.org/2000/svg"
    >
      <g v-for="zone in drawn.zones" :key="zone.topicId">
        <rect class="map-hill" :data-testid="`map-hill-${zone.topicId}`" x="8" :y="zone.y" :width="drawn.width - 16" :height="zone.height" rx="28" />
        <text class="map-zone-title" :class="{ 'map-zone-title--end': !narrow }" :x="narrow ? 110 : drawn.width - 28" :y="zone.y + 30">{{ zone.title }} · {{ zone.done }}/{{ zone.total }}</text>
      </g>
      <path v-if="road" class="map-road" data-testid="map-road" :d="road" />
      <path v-if="travelled" class="map-road map-road--done" data-testid="map-road-done" :d="travelled" />
      <MapStation v-for="s in drawn.stations" :key="s.id" :station="s" :narrow="narrow" />
    </svg>

    <MapLegend />

    <p v-if="finished" class="notice" data-testid="map-finished">¡Tu árbol está lleno de frutos! Has llegado al final del camino; repasa cuando quieras.</p>
    <p v-else-if="!full.nowId" class="muted" data-testid="map-no-now">
      {{ waiting ? 'La IA está preparando tu curso: en cuanto haya una lección lista, aquí aparecerá tu parada.' : 'Todavía no hay ninguna lección lista para estudiar.' }}
    </p>

    <ol class="map-list" data-testid="map-list" aria-label="Paradas del curso">
      <li v-for="zone in full.zones" :key="zone.topicId" class="map-topic">
        <h3>
          {{ topicTitle(zone.topicId) }}
          <span v-if="topicStudy(zone.topicId)" class="muted" :data-testid="`topic-percent-${zone.topicId}`">{{ topicStudy(zone.topicId)?.percent }} %</span>
          <button
            type="button"
            class="map-fold"
            :data-testid="`map-fold-${zone.topicId}`"
            :aria-expanded="collapsed.has(zone.topicId) ? 'false' : 'true'"
            @click="toggle(zone.topicId)"
          >{{ collapsed.has(zone.topicId) ? 'Mostrar en el mapa' : 'Plegar en el mapa' }}</button>
        </h3>
        <ol>
          <li
            v-for="s in stationsOf(zone.topicId)"
            :key="s.id"
            class="map-item"
            :data-testid="`map-item-${s.id}`"
            :aria-current="s.state === 'now' ? 'step' : undefined"
          >
            <RouterLink v-if="s.route" :to="s.route" :data-testid="`map-link-${s.id}`">{{ s.label }}</RouterLink>
            <span v-else>{{ s.label }}</span>
            <UiTag :tone="s.state === 'done' ? 'ok' : s.state === 'now' ? 'now' : 'plain'">{{ stateText(s) }}</UiTag>
            <span v-if="s.kind === 'exam' && examNote(zone.topicId)" class="muted" :data-testid="`exam-best-${zone.topicId}`">{{ examNote(zone.topicId) }}</span>
            <RouterLink v-if="s.exercises?.route" :to="s.exercises.route" :data-testid="`piece-link-${s.exercises.id}`">Ejercicios</RouterLink>
            <span v-else-if="s.exercises" class="muted">Ejercicios en cola</span>
            <UiTag v-if="s.state === 'todo' && s.route">Lista</UiTag>
            <UiTag v-if="s.queued" tone="accent">En cola</UiTag>
            <UiTag v-if="s.failed" tone="accent">Hubo un error</UiTag>
            <UiTag v-if="s.outdated" tone="accent">La lección cambió</UiTag>
          </li>
        </ol>
      </li>
    </ol>
  </section>
</template>
