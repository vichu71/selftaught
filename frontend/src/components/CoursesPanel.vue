<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Course, type CourseStatus } from '@/api/client'
import { useStudyStore } from '@/stores/study'
import UiProgress from '@/ui/UiProgress.vue'
import UiState from '@/ui/UiState.vue'

const emit = defineEmits<{ loaded: [courses: Course[]] }>()

const study = useStudyStore()
const courses = ref<Course[]>([])
const listError = ref(false)
const loading = ref(true)

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
    await study.loadProgress()
    emit('loaded', courses.value)
  } catch {
    listError.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="card" data-testid="courses-panel">
    <h2>Tus cursos</h2>
    <UiState v-if="listError" state="error" message="No se pudieron cargar tus cursos." data-testid="courses-error" />
    <UiState v-else-if="loading" state="loading" message="Cargando tus cursos…" />
    <p v-else-if="courses.length === 0" class="muted" data-testid="no-courses">Todavía no tienes ningún curso. ¡Crea el primero!</p>

    <ul class="documents">
      <li v-for="c in courses" :key="c.id" class="document" :data-testid="`course-${c.id}`">
        <RouterLink :to="`/cursos/${c.id}`" :data-testid="`course-link-${c.id}`">{{ c.title }}</RouterLink>
        <span class="status">{{ labels[c.status] }}</span>
        <UiProgress
          v-if="study.percentByCourse[c.id] !== undefined"
          :value="study.percentByCourse[c.id]"
          :label="`Progreso de ${c.title}`"
          :data-testid="`course-percent-${c.id}`"
        />
      </li>
    </ul>

    <RouterLink to="/nuevo-curso" class="button-link" data-testid="new-course-link">Nuevo curso</RouterLink>
  </section>
</template>
