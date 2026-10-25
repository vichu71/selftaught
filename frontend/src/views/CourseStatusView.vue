<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type Course, type CourseStatus } from '@/api/client'

const route = useRoute()
const course = ref<Course | null>(null)
const notFound = ref(false)
const failed = ref(false)

const labels: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola: lo estamos preparando',
  GENERATING: 'Preparándose ahora mismo',
  READY: 'Listo para estudiar',
  FAILED: 'No se pudo preparar',
}

const status = computed(() => (course.value ? labels[course.value.status] : ''))

onMounted(async () => {
  try {
    course.value = await api.getCourse(String(route.params.id))
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else failed.value = true
  }
})
</script>

<template>
  <main class="page" data-testid="course-view">
    <header class="topbar">
      <h1 v-if="course" data-testid="course-title">{{ course.title }}</h1>
      <h1 v-else>Tu curso</h1>
      <RouterLink to="/" data-testid="back-home">Volver al inicio</RouterLink>
    </header>

    <p v-if="notFound" class="error" role="alert" data-testid="course-not-found">No encontramos este curso.</p>
    <p v-else-if="failed" class="error" role="alert" data-testid="course-error">No se pudo cargar el curso. Inténtalo más tarde.</p>

    <template v-if="course">
      <section class="card">
        <p class="status-line" data-testid="course-status">{{ status }}</p>
        <p class="muted">Este curso lo genera una inteligencia artificial: generado por IA.</p>
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
