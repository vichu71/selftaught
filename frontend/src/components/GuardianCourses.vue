<script setup lang="ts">
import type { CourseStatus, GuardianCourseSummary } from '@/api/client'

defineProps<{ studentId: string; courses: GuardianCourseSummary[] }>()

const states: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola',
  GENERATING: 'Generándose',
  PAUSED: 'En pausa',
  READY: 'Listo',
  FAILED: 'Con error',
}
</script>

<template>
  <section class="card">
    <h2>Cursos</h2>
    <p v-if="courses.length === 0" class="muted" data-testid="guardian-courses-empty">Todavía no tiene cursos.</p>
    <ul v-else class="guardian-list">
      <li v-for="c in courses" :key="c.id" :data-testid="`guardian-course-${c.id}`">
        <RouterLink :to="`/alumnos/${studentId}/cursos/${c.id}`" :data-testid="`guardian-course-link-${c.id}`">{{ c.title }}</RouterLink>
        <span class="muted"> · {{ states[c.status] ?? c.status }}</span>
        <span v-if="c.percent !== undefined"> · {{ c.percent }} %</span>
        <span v-if="c.lessonsTotal + c.examsTotal > 0" class="muted"> ({{ c.lessonsDone }}/{{ c.lessonsTotal }} lecciones, {{ c.examsPassed }}/{{ c.examsTotal }} exámenes)</span>
      </li>
    </ul>
  </section>
</template>
