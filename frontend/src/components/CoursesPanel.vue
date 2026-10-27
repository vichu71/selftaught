<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, api, type Course, type CourseStatus } from '@/api/client'
import DeleteCoursesDialog from '@/components/DeleteCoursesDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useStudyStore } from '@/stores/study'
import UiButton from '@/ui/UiButton.vue'
import UiProgress from '@/ui/UiProgress.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'
import { STAGE_LABEL, plantStage } from '@/world/plantStage'
import UiState from '@/ui/UiState.vue'

const emit = defineEmits<{ loaded: [courses: Course[]] }>()

const study = useStudyStore()
const auth = useAuthStore()
const courses = ref<Course[]>([])
const listError = ref(false)
const loading = ref(true)

// Borrar cursos (F13): solo los adultos; el servidor lo comprueba igual
const selecting = ref(false)
const selected = ref<string[]>([])
const dialogOpen = ref(false)
const deleting = ref(false)
const deleteError = ref('')
const canDelete = computed(() => auth.user?.kind === 'ADULT' && courses.value.length > 0)
const allSelected = computed(() => courses.value.length > 0 && selected.value.length === courses.value.length)
const toDelete = computed(() => courses.value.filter((c) => selected.value.includes(c.id)).map((c) => ({ id: c.id, title: c.title })))

function toggleSelecting() {
  selecting.value = !selecting.value
  selected.value = []
  deleteError.value = ''
}
function toggle(id: string, on: boolean) {
  selected.value = on ? [...selected.value.filter((x) => x !== id), id] : selected.value.filter((x) => x !== id)
}
function toggleAll(on: boolean) {
  selected.value = on ? courses.value.map((c) => c.id) : []
}
async function confirmDelete() {
  if (deleting.value || selected.value.length === 0) return
  deleting.value = true
  deleteError.value = ''
  let removed = false
  try {
    await api.deleteCourses(selected.value)
    removed = true
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) removed = true // ya estaba borrado
    else deleteError.value = e instanceof ApiError && e.status === 409
      ? 'Algún curso se está generando ahora mismo. Espera a que termine para borrarlo.'
      : 'No se pudieron borrar los cursos. Inténtalo de nuevo.'
  }
  if (removed) {
    try {
      courses.value = (await api.listCourses()) ?? []
    } catch {
      listError.value = true
    }
    selecting.value = false
    selected.value = []
  }
  deleting.value = false
}

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

    <div v-if="canDelete" class="select-bar">
      <UiButton data-testid="courses-select-toggle" @click="toggleSelecting">{{ selecting ? 'Cancelar selección' : 'Seleccionar' }}</UiButton>
      <template v-if="selecting">
        <label class="select-all">
          <input type="checkbox" :checked="allSelected" data-testid="courses-select-all" @change="toggleAll(($event.target as HTMLInputElement).checked)" />
          Seleccionar todos
        </label>
        <span role="status" data-testid="courses-selected-count">{{ selected.length === 1 ? '1 seleccionado' : `${selected.length} seleccionados` }}</span>
        <UiButton variant="danger" :disabled="selected.length === 0 || deleting" data-testid="courses-delete-open" @click="dialogOpen = true">Borrar ({{ selected.length }})</UiButton>
      </template>
    </div>
    <p v-if="deleteError" class="error" role="alert" data-testid="courses-delete-error">{{ deleteError }}</p>

    <ul class="garden">
      <li v-for="c in courses" :key="c.id" class="plant-card" :data-testid="`course-${c.id}`">
        <input
          v-if="selecting"
          type="checkbox"
          class="select-box"
          :checked="selected.includes(c.id)"
          :aria-label="`Seleccionar ${c.title}`"
          :data-testid="`course-select-${c.id}`"
          @change="toggle(c.id, ($event.target as HTMLInputElement).checked)"
        />
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
    <DeleteCoursesDialog v-model="dialogOpen" :courses="toDelete" :busy="deleting" @confirm="confirmDelete" />
  </section>
</template>
