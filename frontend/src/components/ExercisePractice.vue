<script setup lang="ts">
import { ref } from 'vue'
import { ApiError, api, type AiGrade, type Exercise, type ExerciseAnswered, type ExerciseResult } from '@/api/client'
import { BLOCKED_TEXT } from '@/stores/course'

const props = defineProps<{ courseId: string; pieceId: string; index: number; exercise: Exercise }>()

const difficulty: Record<Exercise['difficulty'], string> = { LOW: 'Fácil', MEDIUM: 'Normal', HIGH: 'Difícil' }
const verdictLabel: Record<ExerciseResult, string> = { CORRECT: 'Acertado', PARTIAL: 'Casi', INCORRECT: 'Fallado' }
const results: { value: ExerciseResult; label: string }[] = [
  { value: 'CORRECT', label: 'Lo he acertado' },
  { value: 'PARTIAL', label: 'Casi' },
  { value: 'INCORRECT', label: 'No lo he acertado' },
]

const aiMessages: Record<string, string> = {
  AI_GRADING_LIMIT: 'Has usado todas las correcciones de IA de hoy. Puedes autocorregirte.',
  AI_USER_LIMIT: 'Has llegado a tu límite de uso de IA de este mes. Puedes autocorregirte.',
  AI_BUDGET_BLOCKED: 'Se ha alcanzado el límite de uso de IA. Puedes autocorregirte.',
  GRADING_FAILED: 'No se pudo corregir con IA. Puedes autocorregirte.',
  GRADING_BUSY: 'Ya tienes una corrección en marcha. Espera a que termine.',
  AI_GRADING_ALREADY: 'Este intento ya se corrigió con IA.',
  AI_PROVIDER_UNAVAILABLE: 'El servicio de IA no está disponible ahora mismo. Puedes autocorregirte.',
  ANSWER_NOT_ALLOWED: BLOCKED_TEXT,
}

const answer = ref('')
const attempt = ref<ExerciseAnswered | null>(null)
const sending = ref(false)
const self = ref<ExerciseResult | null>(null)
const ai = ref<AiGrade | null>(null)
const aiBusy = ref(false)
const aiTried = ref(false)
const error = ref('')

async function send() {
  if (sending.value || attempt.value) return
  error.value = ''
  if (answer.value.trim() === '') {
    error.value = 'Escribe tu respuesta antes de enviarla.'
    return
  }
  sending.value = true
  try {
    attempt.value = await api.sendExerciseAnswer(props.courseId, props.pieceId, props.index, answer.value)
    self.value = null
    ai.value = null
    aiTried.value = false
  } catch (e) {
    error.value = e instanceof ApiError && e.code === 'STUDY_RATE_LIMITED'
      ? 'Has enviado muchos intentos seguidos. Espera un rato e inténtalo de nuevo.'
      : 'No se pudo enviar tu respuesta. Inténtalo de nuevo.'
  } finally {
    sending.value = false
  }
}

/** Otro intento del mismo ejercicio: se conserva lo escrito para poder corregirlo. */
function retry() {
  attempt.value = null
  self.value = null
  ai.value = null
  aiTried.value = false
  error.value = ''
}

async function mark(result: ExerciseResult) {
  if (!attempt.value) return
  error.value = ''
  try {
    await api.setExerciseResult(props.courseId, props.pieceId, props.index, attempt.value.attemptId, result)
    self.value = result
  } catch {
    error.value = 'No se pudo guardar cómo te fue. Inténtalo de nuevo.'
  }
}

async function grade() {
  if (!attempt.value || aiBusy.value || aiTried.value) return
  aiBusy.value = true
  error.value = ''
  try {
    ai.value = await api.aiGrade(props.courseId, props.pieceId, props.index, attempt.value.attemptId)
    aiTried.value = true
  } catch (e) {
    const code = e instanceof ApiError ? e.code : undefined
    error.value = (code && aiMessages[code]) || 'No se pudo corregir con IA. Inténtalo más tarde.'
    if (code !== 'GRADING_BUSY') aiTried.value = true
  } finally {
    aiBusy.value = false
  }
}
</script>

<template>
  <li class="exercise" :data-testid="`exercise-${index}`">
    <p class="muted">Ejercicio {{ index + 1 }} · {{ difficulty[exercise.difficulty] ?? '' }}</p>
    <p class="statement">{{ exercise.statement }}</p>

    <label :for="`answer-${index}`" class="visually-hidden">Tu respuesta</label>
    <textarea :id="`answer-${index}`" v-model="answer" rows="4" maxlength="2000" :data-testid="`answer-${index}`" :disabled="sending"></textarea>
    <p class="actions">
      <button type="button" class="primary" :data-testid="`send-${index}`" :disabled="sending || attempt !== null" @click="send">Enviar mi respuesta</button>
      <button v-if="attempt" type="button" :data-testid="`retry-${index}`" @click="retry">Probar otra vez</button>
    </p>
    <p v-if="error" class="error" role="alert" :data-testid="`exercise-error-${index}`">{{ error }}</p>

    <section v-if="attempt" :data-testid="`solution-${index}`">
      <h3>Solución</h3>
      <p class="statement">{{ attempt.solution }}</p>
      <p v-if="attempt.explanation" class="statement">{{ attempt.explanation }}</p>

      <p>¿Cómo te fue?</p>
      <div class="choice">
        <button v-for="r in results" :key="r.value" type="button" :aria-pressed="self === r.value ? 'true' : 'false'" :data-testid="`result-${index}-${r.value}`" @click="mark(r.value)">
          {{ r.label }}
        </button>
      </div>

      <button v-if="!aiTried" type="button" :data-testid="`ai-grade-${index}`" :disabled="aiBusy" @click="grade">Corregir con IA</button>
      <p v-if="ai" class="ai-verdict" :data-testid="`ai-verdict-${index}`">
        <strong>{{ verdictLabel[ai.verdict] }}.</strong> {{ ai.comment }}
        <span class="muted">La IA puede equivocarse: decide tú cómo te fue.</span>
      </p>
    </section>
  </li>
</template>
