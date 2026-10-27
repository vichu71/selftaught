<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, exportPath, type CourseStatus, type StopReason } from '@/api/client'
import DeleteCoursesDialog from '@/components/DeleteCoursesDialog.vue'
import { useAuthStore } from '@/stores/auth'
import UiButton from '@/ui/UiButton.vue'
import CourseMap from '@/map/CourseMap.vue'
import { useCourseDetailStore } from '@/stores/courseDetail'
import { useStudyStore } from '@/stores/study'
import { BLOCKED_TEXT } from '@/stores/course'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useCourseDetailStore()
const study = useStudyStore()

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
const status = computed(() => {
  if (!course.value) return ''
  if (course.value.status === 'PENDING_GENERATION' && course.value.waitingForOther) return 'En cola: esperando a que termine tu otro curso'
  return labels[course.value.status]
})

// Reloj de «comprobado hace N s»: solo corre mientras el curso se prepara y se reinicia con cada actualización
const now = ref(Date.now())
let clock: ReturnType<typeof setInterval> | undefined
const seconds = computed(() => (store.updatedAt === null ? 0 : Math.max(0, Math.floor((now.value - store.updatedAt) / 1000))))
function stopClock() {
  if (clock !== undefined) clearInterval(clock)
  clock = undefined
}
watch(() => store.polling, (active) => {
  stopClock()
  if (active) clock = setInterval(() => (now.value = Date.now()), 1000)
}, { immediate: true })
watch(() => store.updatedAt, () => (now.value = Date.now()))
const outline = computed(() => course.value?.outline ?? [])
const progress = computed(() => course.value?.progress)
const paused = computed(() => course.value?.status === 'PAUSED')
const showResume = computed(() => paused.value || store.resumeRequested)
const pauseMessage = computed(() => pauseText[course.value?.stopReason as StopReason] ?? genericPause)
const blocked = computed(() => course.value?.status === 'FAILED' && course.value.stopReason === 'TOPIC_NOT_ALLOWED')
const failedOther = computed(() => course.value?.status === 'FAILED' && !blocked.value)
const hasDone = computed(() => (progress.value?.done ?? 0) > 0)

// Borrar el curso (F13): solo adultos; un 404 (ya borrado, p. ej. por un doble clic) cuenta como hecho
const deleteOpen = ref(false)
const deleting = ref(false)
const deleteError = ref('')
async function confirmDelete() {
  if (deleting.value || !course.value) return
  deleting.value = true
  deleteError.value = ''
  try {
    await api.deleteCourse(course.value.id)
    await router.push('/')
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) await router.push('/')
    else deleteError.value = e instanceof ApiError && e.status === 409
      ? 'Este curso se está generando ahora mismo. Espera a que termine para borrarlo.'
      : 'No se pudo borrar el curso. Inténtalo de nuevo.'
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  study.reset()
  await store.start(String(route.params.id))
  void study.loadOverview(String(route.params.id))
})
// Cuando el curso avanza (llegan piezas nuevas) se vuelve a pedir el estado de estudio
watch(() => course.value?.progress?.done, (now, before) => {
  if (before !== undefined && now !== before) void study.loadOverview(String(route.params.id))
})
watch(() => route.params.id, (id, old) => {
  if (id && id !== old && route.name === 'course') void store.start(String(id))
})
onBeforeUnmount(() => {
  stopClock()
  store.stop()
})
</script>

<template>
  <div class="page wide" data-testid="course-view">
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
        <p v-if="course.version" class="muted" data-testid="course-version">Versión {{ course.version }}</p>
        <p v-if="course.copy" class="muted" data-testid="copy-notice">Este curso es una copia de otro (versión {{ course.copy.originVersion }} del original).</p>
        <p><RouterLink :to="`/cursos/${course.id}/editar`" data-testid="edit-link">Editar o compartir este curso</RouterLink></p>
        <p v-if="auth.user?.kind === 'ADULT'">
          <UiButton variant="danger" data-testid="course-delete-open" @click="deleteOpen = true">Borrar curso</UiButton>
        </p>
        <p v-if="deleteError" class="error" role="alert" data-testid="course-delete-error">{{ deleteError }}</p>
        <p v-if="study.overview?.percent !== undefined" data-testid="study-progress">Tu avance: {{ study.overview.percent }} %</p>
        <p v-if="store.polling" class="activity" role="status" data-testid="activity">
          <span class="spinner" aria-hidden="true"></span>
          <span data-testid="updated">Comprobado hace {{ seconds }} s</span>
        </p>
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

      <CourseMap v-if="outline.length > 0" :course-id="course.id" :outline="outline" :study="study.overview" :status="course.status" />

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
      <DeleteCoursesDialog v-model="deleteOpen" :courses="[{ id: course.id, title: course.title }]" :busy="deleting" @confirm="confirmDelete" />
    </template>
  </div>
</template>
