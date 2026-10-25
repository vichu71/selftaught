<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { exportPath, type CourseStatus, type StopReason } from '@/api/client'
import CourseOutline from '@/components/CourseOutline.vue'
import { useCourseDetailStore } from '@/stores/courseDetail'
import { BLOCKED_TEXT } from '@/stores/course'

const route = useRoute()
const store = useCourseDetailStore()

const labels: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola: lo estamos preparando',
  GENERATING: 'Preparándose ahora mismo',
  PAUSED: 'En pausa',
  READY: 'Listo para estudiar',
  FAILED: 'No se pudo preparar',
}

// Cada motivo de pausa se explica con palabras sencillas; nunca se enseña el código
const pauseText: Record<string, string> = {
  BUDGET: 'Se ha alcanzado el límite de uso de IA. Lo que ya está hecho se conserva; cuando puedas seguir, pulsa «Reanudar».',
  PROVIDER: 'El proveedor de IA no está disponible ahora mismo. Lo que ya está hecho se conserva; pulsa «Reanudar» para seguir.',
  CONTENT: 'Algunas piezas no se pudieron preparar. Lo demás está listo; pulsa «Reanudar» para intentarlo de nuevo con las que faltan.',
}
const genericPause = 'El curso está en pausa. Lo que ya está hecho se conserva; pulsa «Reanudar» para seguir.'

const course = computed(() => store.course)
const status = computed(() => (course.value ? labels[course.value.status] : ''))
const outline = computed(() => course.value?.outline ?? [])
const progress = computed(() => course.value?.progress)
const paused = computed(() => course.value?.status === 'PAUSED')
const showResume = computed(() => paused.value || store.resumeRequested)
const pauseMessage = computed(() => pauseText[course.value?.stopReason as StopReason] ?? genericPause)
const blocked = computed(() => course.value?.status === 'FAILED' && course.value.stopReason === 'TOPIC_NOT_ALLOWED')
const failedOther = computed(() => course.value?.status === 'FAILED' && !blocked.value)
const hasDone = computed(() => (progress.value?.done ?? 0) > 0)

onMounted(() => void store.start(String(route.params.id)))
watch(() => route.params.id, (id, old) => {
  if (id && id !== old && route.name === 'course') void store.start(String(id))
})
onBeforeUnmount(() => store.stop())
</script>

<template>
  <main class="page" data-testid="course-view">
    <header class="topbar">
      <h1 v-if="course" data-testid="course-title">{{ course.title }}</h1>
      <h1 v-else>Tu curso</h1>
      <RouterLink to="/" data-testid="back-home">Volver al inicio</RouterLink>
    </header>

    <p v-if="store.notFound" class="error" role="alert" data-testid="course-not-found">No encontramos este curso.</p>
    <p v-else-if="store.failed && !course" class="error" role="alert" data-testid="course-error">No se pudo cargar el curso. Inténtalo más tarde.</p>

    <template v-if="course">
      <section class="card">
        <p class="status-line" data-testid="course-status">{{ status }}</p>
        <p v-if="progress && progress.total > 0" data-testid="progress">{{ progress.done }} de {{ progress.total }} piezas preparadas</p>
        <p class="muted">Este curso lo genera una inteligencia artificial: generado por IA.</p>

        <p v-if="blocked" class="error" role="alert" data-testid="blocked-message">{{ BLOCKED_TEXT }}</p>
        <p v-else-if="failedOther" class="error" role="alert" data-testid="failed-notice">No se pudo preparar este curso. Prueba a crear uno nuevo.</p>

        <div v-if="showResume" class="pause" data-testid="pause-section">
          <p v-if="paused" role="status" data-testid="pause-notice">{{ pauseMessage }}</p>
          <button type="button" class="primary" data-testid="resume" :disabled="store.resuming || store.resumeRequested" @click="store.resume()">
            {{ store.resumeRequested ? 'Reanudando…' : 'Reanudar' }}
          </button>
          <p v-if="store.resumeError" class="error" role="alert" data-testid="resume-error">{{ store.resumeError }}</p>
        </div>

        <a v-if="hasDone" :href="exportPath(course.id)" download class="button-link" data-testid="export-link">Descargar el curso (JSON)</a>
      </section>

      <section v-if="outline.length > 0" class="card">
        <h2>Temario</h2>
        <CourseOutline :course-id="course.id" :outline="outline" />
      </section>

      <section class="card" data-testid="course-sheet">
        <h2>Lo que acordamos</h2>
        <ul>
          <li>Enfoque: {{ course.sheet.focus }}</li>
          <li>Tu nivel: {{ course.sheet.startLevel }}</li>
          <li>Tu objetivo: {{ course.sheet.goal }}</li>
          <li>{{ course.sheet.topicsCount }} temas{{ course.sheet.examPerTopic ? ', con examen en cada uno' : ', sin examen' }}</li>
          <li v-if="course.documents.length > 0">Documentos: {{ course.documents.map((d) => d.name).join(', ') }}</li>
        </ul>
      </section>
    </template>
  </main>
</template>
