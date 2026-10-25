<script setup lang="ts">
import { ref } from 'vue'
import { ApiError, api, type ExamResult } from '@/api/client'
import ExamResultView from '@/components/ExamResultView.vue'

const props = defineProps<{ courseId: string; pieceId: string }>()

const attemptId = ref('')
const questions = ref<{ text: string; options: string[] }[]>([])
const chosen = ref<(number | null)[]>([])
const result = ref<ExamResult | null>(null)
const busy = ref(false)
const warned = ref(false)
const error = ref('')

const missing = () => chosen.value.filter((c) => c === null || c === undefined).length

async function start() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  warned.value = false
  try {
    const started = await api.startExam(props.courseId, props.pieceId)
    attemptId.value = started.attemptId
    questions.value = started.questions
    chosen.value = started.questions.map(() => null)
    result.value = null
  } catch (e) {
    error.value = e instanceof ApiError && e.status === 409 ? 'Este examen todavía no está listo.' : 'No se pudo empezar el examen. Inténtalo de nuevo.'
  } finally {
    busy.value = false
  }
}

async function submit() {
  if (busy.value) return
  // Las preguntas sin contestar cuentan como falladas: primero se avisa y se entrega con el segundo clic
  if (missing() > 0 && !warned.value) {
    warned.value = true
    return
  }
  busy.value = true
  error.value = ''
  try {
    result.value = await api.submitExam(props.courseId, props.pieceId, attemptId.value, chosen.value.map((c) => c ?? null))
  } catch (e) {
    error.value = e instanceof ApiError && e.code === 'STUDY_RATE_LIMITED'
      ? 'Has enviado muchos intentos seguidos. Espera un rato e inténtalo de nuevo.'
      : 'No se pudo entregar el examen. Inténtalo de nuevo.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="exam-take" data-testid="exam-take">
    <template v-if="result">
      <ExamResultView :result="result" />
      <button type="button" class="primary" data-testid="retry-exam" :disabled="busy" @click="start">Repetir el examen</button>
    </template>

    <template v-else-if="questions.length > 0">
      <ol class="exam">
        <li v-for="(q, i) in questions" :key="i" class="exercise" :data-testid="`question-${i}`">
          <p class="statement">{{ q.text }}</p>
          <label v-for="(option, j) in q.options" :key="j" class="check">
            <input v-model="chosen[i]" type="radio" :name="`q-${i}`" :value="j" :data-testid="`option-${i}-${j}`" />
            <span>{{ option }}</span>
          </label>
        </li>
      </ol>
      <p v-if="warned && missing() > 0" role="alert" data-testid="unanswered-warning">
        Te faltan {{ missing() }} preguntas sin contestar y contarán como falladas. Pulsa «Entregar igualmente» para entregarlo así.
      </p>
      <button type="button" class="primary" data-testid="submit-exam" :disabled="busy" @click="submit">
        {{ warned && missing() > 0 ? 'Entregar igualmente' : 'Entregar' }}
      </button>
    </template>

    <template v-else>
      <p>Elige una opción en cada pregunta y entrégalo para ver tu nota. Se puede repetir las veces que quieras y no acredita nada: es para que te midas.</p>
      <button type="button" class="primary" data-testid="start-exam" :disabled="busy" @click="start">Empezar el examen</button>
    </template>

    <p v-if="error" class="error" role="alert" data-testid="exam-error">{{ error }}</p>
  </div>
</template>
