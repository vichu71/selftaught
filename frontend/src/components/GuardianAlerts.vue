<script setup lang="ts">
import type { GuardianAlert, GuardianAlertType } from '@/api/client'

const props = defineProps<{ studentId: string; alerts: GuardianAlert[] }>()

const labels: Record<GuardianAlertType, string> = {
  TUTOR_QUESTION_BLOCKED: 'Pregunta bloqueada al tutor',
  TUTOR_ANSWER_FILTERED: 'Respuesta del tutor filtrada',
  COURSE_TOPIC_BLOCKED: 'Tema de curso rechazado',
  STUDY_ANSWER_BLOCKED: 'Respuesta de ejercicio no enviada a corregir',
  COURSE_CONTENT_BLOCKED: 'Contenido de curso no generado',
}

const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/** Los avisos del tutor llevan a su conversación; el resto, al curso (si todavía existe). */
function link(alert: GuardianAlert): string | null {
  if (!alert.courseId) return null
  if (alert.pieceId && alert.type.startsWith('TUTOR_')) return `/alumnos/${props.studentId}/cursos/${alert.courseId}/lecciones/${alert.pieceId}`
  return alert.courseTitle ? `/alumnos/${props.studentId}/cursos/${alert.courseId}` : null
}
</script>

<template>
  <section class="card" data-testid="guardian-alerts-section">
    <h2>Avisos</h2>
    <p v-if="alerts.length === 0" class="muted" data-testid="guardian-alerts-empty">No hay nada que avisar: el filtro no ha parado nada.</p>
    <ul v-else class="guardian-list" data-testid="guardian-alerts">
      <li v-for="(alert, i) in alerts" :key="`${alert.type}-${alert.at}-${i}`" :data-testid="`guardian-alert-${i}`">
        <strong>{{ labels[alert.type] ?? 'Aviso' }}</strong>
        <span class="muted"> · {{ when(alert.at) }}</span>
        <span v-if="alert.courseTitle"> · {{ alert.courseTitle }}</span>
        <span v-if="alert.lessonTitle"> · {{ alert.lessonTitle }}</span>
        <span v-if="alert.topic"> · «{{ alert.topic }}»</span>
        <RouterLink v-if="link(alert)" :to="link(alert) as string" :data-testid="`guardian-alert-link-${i}`"> Ver</RouterLink>
      </li>
    </ul>
  </section>
</template>
