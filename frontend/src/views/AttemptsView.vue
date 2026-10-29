<script setup lang="ts">
import BackLink from '@/ui/BackLink.vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type Attempts, type ExamResult, type ExerciseResult } from '@/api/client'
import ExamResultView from '@/components/ExamResultView.vue'

const route = useRoute()
const courseId = computed(() => String(route.params.id))
const pieceId = computed(() => String(route.params.pieceId))

const data = ref<Attempts | null>(null)
const notFound = ref(false)
const failed = ref(false)
const opened = ref<ExamResult | null>(null)
const detailError = ref(false)

const label: Record<ExerciseResult, string> = { CORRECT: 'Acertado', PARTIAL: 'Casi', INCORRECT: 'Fallado' }
const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' })

onMounted(async () => {
  try {
    data.value = await api.getAttempts(courseId.value, pieceId.value)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else failed.value = true
  }
})

async function open(attemptId: string) {
  detailError.value = false
  try {
    opened.value = await api.getExamAttempt(courseId.value, pieceId.value, attemptId)
  } catch {
    detailError.value = true
  }
}
</script>

<template>
  <div class="page wide" data-testid="attempts-view">
    <header class="topbar">
      <h1>Tus intentos</h1>
      <BackLink :to="`/cursos/${courseId}/piezas/${pieceId}`" label="Lección" aria-label="Volver a la lección" data-testid="back-piece" />
    </header>

    <p v-if="notFound" class="error" role="alert" data-testid="attempts-not-found">No encontramos esta parte del curso.</p>
    <p v-else-if="failed" class="error" role="alert" data-testid="attempts-error">No se pudieron cargar tus intentos. Inténtalo más tarde.</p>

    <template v-else-if="data">
      <p v-if="data.attempts.length === 0" class="card muted" data-testid="attempts-empty">Todavía no tienes intentos aquí.</p>

      <ul v-else-if="data.kind === 'EXERCISES'" class="documents card">
        <li v-for="a in data.attempts" :key="a.id" class="document result" :data-testid="`attempt-${a.id}`">
          <p class="muted">Ejercicio {{ a.itemIndex + 1 }} · {{ when(a.createdAt) }}</p>
          <p class="statement">{{ a.answer }}</p>
          <p>
            <span class="status">{{ a.selfResult ? label[a.selfResult] : 'Sin valorar' }}</span>
            <template v-if="a.aiResult"> <span class="status">IA: {{ label[a.aiResult] }}</span></template>
          </p>
          <p v-if="a.aiComment" class="muted">{{ a.aiComment }}</p>
        </li>
      </ul>

      <ul v-else class="documents card">
        <li v-for="a in data.attempts" :key="a.id" class="document result" :data-testid="`attempt-${a.id}`">
          <p>
            {{ a.correct }} de {{ a.total }} · {{ a.percent }} % · <span class="status">{{ a.passed ? 'Superado' : 'Todavía no' }}</span>
            <span class="muted"> · {{ when(a.submittedAt) }}</span>
          </p>
          <button type="button" :data-testid="`attempt-open-${a.id}`" @click="open(a.id)">Ver qué fallé</button>
        </li>
      </ul>

      <p v-if="detailError" class="error" role="alert">No se pudo abrir ese intento.</p>
      <section v-if="opened" class="card">
        <ExamResultView :result="opened" />
      </section>
    </template>
  </div>
</template>
