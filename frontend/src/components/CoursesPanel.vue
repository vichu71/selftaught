<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Course, type CourseStatus } from '@/api/client'
import { useStudyStore } from '@/stores/study'

const study = useStudyStore()
const courses = ref<Course[]>([])
const listError = ref(false)

const labels: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola',
  GENERATING: 'Preparándose',
  PAUSED: 'En pausa',
  READY: 'Listo',
  FAILED: 'No se pudo preparar',
}

onMounted(async () => {
  try {
    courses.value = (await api.listCourses()) ?? []
    void study.loadProgress()
  } catch {
    listError.value = true
  }
})
</script>

<template>
  <section class="card" data-testid="courses-panel">
    <h2>Tus cursos</h2>
    <p v-if="listError" class="error" role="alert" data-testid="courses-error">No se pudieron cargar tus cursos.</p>
    <p v-else-if="courses.length === 0" class="muted" data-testid="no-courses">Todavía no tienes ningún curso. ¡Crea el primero!</p>

    <ul class="documents">
      <li v-for="c in courses" :key="c.id" class="document" :data-testid="`course-${c.id}`">
        <RouterLink :to="`/cursos/${c.id}`" :data-testid="`course-link-${c.id}`">{{ c.title }}</RouterLink>
        <span class="status">{{ labels[c.status] }}</span>
        <span v-if="study.percentByCourse[c.id] !== undefined" class="muted" :data-testid="`course-percent-${c.id}`">{{ study.percentByCourse[c.id] }} %</span>
      </li>
    </ul>

    <RouterLink to="/nuevo-curso" class="button-link" data-testid="new-course-link">Nuevo curso</RouterLink>
  </section>
</template>
