<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type Piece } from '@/api/client'
import Breadcrumb from '@/layouts/Breadcrumb.vue'
import ExamTake from '@/components/ExamTake.vue'
import ExercisePractice from '@/components/ExercisePractice.vue'
import LessonControls from '@/components/LessonControls.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import TutorChat from '@/components/TutorChat.vue'
import TutorLauncher from '@/components/TutorLauncher.vue'

const route = useRoute()
const piece = ref<Piece | null>(null)
const notFound = ref(false)
const failed = ref(false)

const courseId = computed(() => String(route.params.id))
const done = computed(() => piece.value?.status === 'DONE')
const sources = computed(() => piece.value?.sources ?? [])
const kindLabel = computed(() => (piece.value?.kind === 'LESSON' ? 'Lección' : piece.value?.kind === 'EXERCISES' ? 'Ejercicios' : 'Examen del tema'))

async function load() {
  piece.value = null
  notFound.value = false
  failed.value = false
  try {
    piece.value = await api.getPiece(courseId.value, String(route.params.pieceId))
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else failed.value = true
  }
}

onMounted(load)
watch(() => route.params.pieceId, (id, old) => {
  if (id && id !== old && route.name === 'piece') void load()
})
</script>

<template>
  <div class="page wide" data-testid="piece-view">
    <Breadcrumb :items="[{ label: 'Mapa', to: '/' }, { label: 'Curso', to: `/cursos/${courseId}` }, { label: piece?.title ?? 'Lección' }]" />
    <header class="topbar">
      <h1 v-if="piece" data-testid="piece-title">{{ kindLabel }}: {{ piece.title }}</h1>
      <h1 v-else>Tu curso</h1>
      <RouterLink :to="`/cursos/${courseId}`" data-testid="back-course">Volver al curso</RouterLink>
    </header>

    <p v-if="notFound" class="error" role="alert" data-testid="piece-not-found">No encontramos esta página del curso.</p>
    <p v-else-if="failed" class="error" role="alert" data-testid="piece-error">No se pudo cargar. Inténtalo más tarde.</p>

    <template v-if="piece">
      <p v-if="piece.status === 'PENDING'" class="card" data-testid="piece-pending">Esta parte del curso se está preparando. Vuelve en un rato.</p>
      <p v-else-if="piece.status === 'FAILED'" class="card" data-testid="piece-failed">Esta parte del curso no se pudo preparar.</p>

      <article v-if="done" class="card">
        <template v-if="piece.kind === 'LESSON'">
          <div data-testid="lesson-reader" class="lesson-reader">
          <div data-testid="lesson">
            <MarkdownContent :source="piece.markdown ?? ''" />
          </div>
          </div>
          <LessonControls :key="piece.id" :course-id="courseId" :piece-id="piece.id" :done="piece.done === true" :navigation="piece.navigation" />
          <TutorLauncher :key="`launcher-${piece.id}`">
            <TutorChat :course-id="courseId" :piece-id="piece.id" />
          </TutorLauncher>
        </template>
        <template v-else-if="piece.kind === 'EXERCISES'">
          <ol class="exercises" data-testid="exercises">
            <ExercisePractice v-for="(exercise, i) in piece.exercises ?? []" :key="`${piece.id}-${i}`" :course-id="courseId" :piece-id="piece.id" :index="i" :exercise="exercise" />
          </ol>
          <p><RouterLink :to="`/cursos/${courseId}/piezas/${piece.id}/intentos`" data-testid="history-link">Ver mis intentos anteriores</RouterLink></p>
        </template>
        <template v-else>
          <ExamTake :key="piece.id" :course-id="courseId" :piece-id="piece.id" />
          <p><RouterLink :to="`/cursos/${courseId}/piezas/${piece.id}/intentos`" data-testid="history-link">Ver mis intentos anteriores</RouterLink></p>
        </template>

        <section v-if="sources.length > 0" data-testid="sources">
          <h2>Fuentes</h2>
          <ul>
            <li v-for="s in sources" :key="s.n">Fuente {{ s.n }}<template v-if="s.page"> (página {{ s.page }})</template></li>
          </ul>
        </section>

        <p class="ai-notice">Contenido generado por IA: puede tener errores. Contrástalo con tus apuntes o con tu profesor.</p>
      </article>
    </template>
  </div>
</template>
