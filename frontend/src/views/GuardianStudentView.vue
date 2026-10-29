<script setup lang="ts">
import BackLink from '@/ui/BackLink.vue'
import PageHeader from '@/ui/PageHeader.vue'
import { Users as PageIcon } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type GuardianAlert, type GuardianConversation, type GuardianOverview } from '@/api/client'
import GuardianAlerts from '@/components/GuardianAlerts.vue'
import GuardianCourses from '@/components/GuardianCourses.vue'
import GuardianUsage from '@/components/GuardianUsage.vue'

const route = useRoute()
const studentId = computed(() => String(route.params.id))

const overview = ref<GuardianOverview | null>(null)
const alerts = ref<GuardianAlert[]>([])
const conversations = ref<GuardianConversation[]>([])
const loading = ref(true)
const notFound = ref(false)
const failed = ref(false)

const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

onMounted(async () => {
  const [o, a, c] = await Promise.allSettled([api.getGuardianOverview(studentId.value), api.getGuardianAlerts(studentId.value), api.getGuardianConversations(studentId.value)])
  if (o.status === 'fulfilled') overview.value = o.value
  if (a.status === 'fulfilled') alerts.value = a.value ?? []
  if (c.status === 'fulfilled') conversations.value = c.value ?? []
  const errors = [o, a, c].filter((r): r is PromiseRejectedResult => r.status === 'rejected').map((r) => r.reason)
  notFound.value = errors.some((e) => e instanceof ApiError && e.status === 404)
  failed.value = !notFound.value && errors.length > 0
  loading.value = false
})
</script>

<template>
  <div class="page wide" data-testid="guardian-student">
    <PageHeader :icon="PageIcon">
      <h1 v-if="overview" data-testid="guardian-name">Actividad de {{ overview.student.displayName }}</h1>
      <h1 v-else>Actividad del alumno</h1>
      <BackLink to="/" label="Inicio" aria-label="Volver al inicio" data-testid="guardian-back" />
    </PageHeader>

    <p v-if="loading" class="muted" data-testid="guardian-loading">Cargando…</p>
    <p v-else-if="notFound" class="error" role="alert" data-testid="guardian-not-found">No encontramos a este alumno entre los tuyos.</p>
    <template v-else>
      <p v-if="failed" class="error" role="alert" data-testid="guardian-error">No se ha podido cargar todo. Inténtalo más tarde.</p>
      <p class="muted" data-testid="guardian-readonly-notice">Solo lectura. Tu alumno sabe que puedes ver su actividad.</p>

      <template v-if="overview">
        <p v-if="!overview.student.active" class="notice" data-testid="guardian-inactive">Este alumno está dado de baja: puedes ver su actividad, pero no puede entrar.</p>
        <p v-if="overview.lastActivityAt" class="muted" data-testid="guardian-last-activity">Última actividad: {{ when(overview.lastActivityAt) }}</p>
      </template>

      <GuardianAlerts :student-id="studentId" :alerts="alerts" />
      <GuardianUsage v-if="overview" :usage="overview.usage" />
      <GuardianCourses v-if="overview" :student-id="studentId" :courses="overview.courses" />

      <section class="card">
        <h2>Conversaciones con el tutor</h2>
        <p v-if="conversations.length === 0" class="muted" data-testid="guardian-conversations-empty">Todavía no ha hablado con el tutor.</p>
        <ul v-else class="guardian-list">
          <li v-for="c in conversations" :key="c.pieceId" :data-testid="`guardian-conversation-${c.pieceId}`">
            <RouterLink :to="`/alumnos/${studentId}/cursos/${c.courseId}/lecciones/${c.pieceId}`" :data-testid="`guardian-conversation-link-${c.pieceId}`">
              {{ c.lessonTitle ?? 'Lección' }}<template v-if="c.courseTitle"> · {{ c.courseTitle }}</template>
            </RouterLink>
            <span class="muted"> · {{ c.messages }} mensajes</span>
            <span v-if="c.blocked > 0" class="error"> · {{ c.blocked }} {{ c.blocked === 1 ? 'pregunta bloqueada' : 'preguntas bloqueadas' }}</span>
            <span v-if="c.filtered > 0" class="error"> · {{ c.filtered }} {{ c.filtered === 1 ? 'respuesta filtrada' : 'respuestas filtradas' }}</span>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>
