<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Course, type CourseStatus } from '@/api/client'
import { useStudyStore } from '@/stores/study'
import UiProgress from '@/ui/UiProgress.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'
import { STAGE_LABEL, plantStage } from '@/world/plantStage'
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

const stageOf = (id: string) => plantStage(study.percentByCourse[id])
// Sin porcentaje (en cola o generándose) la planta es una semilla que todavía se está preparando
const stageText = (c: Course) => (study.percentByCourse[c.id] === undefined ? 'Preparándose' : STAGE_LABEL[stageOf(c.id)])

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

    <ul class="garden">
      <li v-for="c in courses" :key="c.id" class="plant-card" :data-testid="`course-${c.id}`">
        <PlantIllustration
          :stage="stageOf(c.id)"
          :label="`${stageText(c)}: ${c.title}`"
          :data-testid="`plant-${c.id}`"
        />
        <RouterLink :to="`/cursos/${c.id}`" :data-testid="`course-link-${c.id}`">{{ c.title }}</RouterLink>
        <span class="status">{{ labels[c.status] }}</span>
        <span class="plant-stage" :data-testid="`plant-stage-${c.id}`">{{ stageText(c) }}</span>
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
