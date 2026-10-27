import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api, type IntakeQuestion } from '@/api/client'

export type Step = 'topic' | 'material' | 'questions' | 'summary' | 'blocked'

export const MIN_TOPIC = 3
export const MAX_TOPIC = 200
export const MAX_DOCUMENTS = 5
export const BLOCKED_TEXT = 'Contenido inapropiado. Consulta con tu adulto.'

const intakeMessages: Record<string, string> = {
  RATE_LIMITED: 'Has hecho muchas consultas seguidas. Espera un rato e inténtalo de nuevo.',
  COURSE_LIMIT_REACHED: 'Tienes varios cursos pendientes de preparar. Espera a que terminen para crear otro.',
  AI_USER_LIMIT: 'Has llegado a tu límite de uso de este mes.',
  AI_BUDGET_BLOCKED: 'Se ha alcanzado el límite de uso de este mes.',
}

const createMessages: Record<string, string> = {
  INTAKE_EXPIRED: 'La consulta ha caducado. Empieza de nuevo.',
  COURSE_LIMIT_REACHED: 'Tienes varios cursos pendientes de preparar. Espera a que terminen para crear otro.',
  NOT_FOUND: 'Alguno de los documentos ya no está disponible. Vuelve atrás y elige otros.',
  ILLUSTRATIONS_UNAVAILABLE: 'Las ilustraciones no están disponibles ahora. Desactiva «Con ilustraciones» y vuelve a intentarlo.',
}

/** El asistente en pasos. El tema y las respuestas viven aquí (en memoria), así que no se pierden si un paso falla ni al ir y volver. */
export const useCourseStore = defineStore('course', () => {
  const step = ref<Step>('topic')
  const topic = ref('')
  const topicError = ref('')
  const wantsMaterial = ref<boolean | null>(null)
  const documentIds = ref<string[]>([])
  const docsError = ref('')

  const intakeId = ref('')
  const questions = ref<IntakeQuestion[]>([])
  const chosen = ref<Record<string, string>>({})

  const thinking = ref(false)
  const intakeError = ref('')
  const creating = ref(false)
  const createError = ref('')
  const expired = ref(false)
  /** «Con ilustraciones» (F12): apagado por defecto y solo se activa desde el último paso. */
  const illustrations = ref(false)

  const trimmedTopic = computed(() => topic.value.trim())

  function reset() {
    step.value = 'topic'
    topic.value = ''
    topicError.value = ''
    wantsMaterial.value = null
    documentIds.value = []
    docsError.value = ''
    intakeId.value = ''
    questions.value = []
    chosen.value = {}
    thinking.value = false
    intakeError.value = ''
    creating.value = false
    createError.value = ''
    expired.value = false
    illustrations.value = false
  }

  function submitTopic() {
    const length = [...trimmedTopic.value].length
    if (length < MIN_TOPIC || length > MAX_TOPIC) {
      topicError.value = `Escribe entre ${MIN_TOPIC} y ${MAX_TOPIC} letras.`
      return
    }
    topicError.value = ''
    topic.value = trimmedTopic.value
    step.value = 'material'
  }

  function answerFor(question: IntakeQuestion): string {
    return chosen.value[question.id] ?? question.options.find((o) => o.isDefault)?.id ?? question.options[0].id
  }

  /** Pregunta al asistente (paso «¿Tienes información?»). Con «Sí» hace falta elegir de 1 a 5 documentos. */
  async function askAssistant() {
    if (thinking.value) return
    docsError.value = ''
    const ids = wantsMaterial.value ? documentIds.value : []
    if (wantsMaterial.value && (ids.length < 1 || ids.length > MAX_DOCUMENTS)) {
      docsError.value = `Elige entre 1 y ${MAX_DOCUMENTS} documentos, o contesta que no.`
      return
    }
    thinking.value = true
    intakeError.value = ''
    try {
      const result = await api.startIntake(topic.value, ids)
      intakeId.value = result.intakeId
      questions.value = result.questions
      chosen.value = Object.fromEntries(result.questions.map((q) => [q.id, q.options.find((o) => o.isDefault)?.id ?? q.options[0].id]))
      step.value = questions.value.length > 0 ? 'questions' : 'summary'
    } catch (e) {
      if (e instanceof ApiError && e.code === 'TOPIC_NOT_ALLOWED') {
        step.value = 'blocked'
      } else {
        intakeError.value = (e instanceof ApiError && e.code && intakeMessages[e.code]) || 'No hemos podido preparar tu curso ahora. Inténtalo de nuevo.'
      }
    } finally {
      thinking.value = false
    }
  }

  function backFromBlocked() {
    step.value = 'topic'
  }

  function back() {
    if (step.value === 'summary') step.value = questions.value.length > 0 ? 'questions' : 'material'
    else if (step.value === 'questions') step.value = 'material'
    else if (step.value === 'material') step.value = 'topic'
    intakeError.value = ''
    createError.value = ''
  }

  function toSummary() {
    step.value = 'summary'
  }

  /** Crea el curso con las respuestas. Devuelve su id, o `null` si falló (el motivo queda en `createError`). */
  async function accept(): Promise<string | null> {
    if (creating.value) return null
    creating.value = true
    createError.value = ''
    expired.value = false
    try {
      const answers = questions.value.map((q) => ({ questionId: q.id, optionId: answerFor(q) }))
      // Sin el interruptor la llamada es exactamente la de siempre (F12: apagado por defecto)
      const course = illustrations.value ? await api.createCourse(intakeId.value, answers, true) : await api.createCourse(intakeId.value, answers)
      return course.id
    } catch (e) {
      const code = e instanceof ApiError ? e.code : undefined
      expired.value = code === 'INTAKE_EXPIRED'
      createError.value = (code && createMessages[code]) || 'No se pudo crear el curso. Inténtalo de nuevo.'
      return null
    } finally {
      creating.value = false
    }
  }

  return {
    step, topic, topicError, wantsMaterial, documentIds, docsError, intakeId, questions, chosen, thinking, intakeError, creating, createError,
    expired, illustrations, reset, submitTopic, answerFor, askAssistant, backFromBlocked, back, toSummary, accept,
  }
})
