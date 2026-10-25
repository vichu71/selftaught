<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ApiError, api, type TutorMessage } from '@/api/client'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ courseId: string; pieceId: string }>()

const MAX_QUESTION = 1000

const errorMessages: Record<string, string> = {
  AI_USER_LIMIT: 'Has llegado a tu límite de uso de IA de este mes. Inténtalo el mes que viene.',
  AI_BUDGET_BLOCKED: 'Se ha alcanzado el límite de uso de IA. Inténtalo más adelante.',
  TUTOR_RATE_LIMITED: 'Has enviado muchos mensajes seguidos. Espera un rato e inténtalo de nuevo.',
  TUTOR_BUSY: 'Tu tutor tiene otra respuesta en marcha. Espera a que termine.',
  TUTOR_FAILED: 'No he podido responder a eso. Prueba a preguntarlo de otra forma.',
  AI_PROVIDER_UNAVAILABLE: 'El servicio de IA no está disponible ahora mismo. Inténtalo en un rato.',
}

const auth = useAuthStore()
const isMinor = computed(() => auth.user?.kind === 'MINOR')

const messages = ref<TutorMessage[]>([])
const canClear = ref(!isMinor.value)
const question = ref('')
const sending = ref(false)
const error = ref('')
const confirming = ref(false)
const end = ref<HTMLElement | null>(null)
const input = ref<HTMLTextAreaElement | null>(null)

onMounted(async () => {
  try {
    const thread = await api.getTutorThread(props.courseId, props.pieceId)
    messages.value = thread?.messages ?? []
    canClear.value = thread?.canClear ?? !isMinor.value
  } catch (e) {
    // La lección sigue funcionando y se puede preguntar aunque no se haya podido cargar la conversación
    if (e instanceof ApiError) error.value = 'No se pudo cargar la conversación anterior. Puedes seguir preguntando.'
  }
})

function sources(m: TutorMessage) {
  return m.sources ?? []
}

function explain(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code && errorMessages[e.code]) return errorMessages[e.code]
    if (e.status === 409) return 'Esta lección todavía no está lista para preguntar.'
  }
  return 'No se pudo enviar tu pregunta. Inténtalo de nuevo.'
}

async function send() {
  const text = question.value.trim()
  if (sending.value || text === '') return
  sending.value = true
  error.value = ''
  try {
    const answer = await api.askTutor(props.courseId, props.pieceId, text)
    messages.value.push(answer.user, answer.tutor)
    question.value = ''
    await nextTick()
    end.value?.scrollIntoView?.({ block: 'nearest' })
  } catch (e) {
    error.value = explain(e) // la pregunta se queda en la caja para poder reenviarla
  } finally {
    sending.value = false
    await nextTick() // el cuadro estaba desactivado mientras se esperaba: se devuelve el foco para seguir escribiendo
    input.value?.focus?.()
  }
}

function onEnter(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  void send()
}

async function clear() {
  error.value = ''
  try {
    await api.clearTutor(props.courseId, props.pieceId)
    messages.value = []
    confirming.value = false
  } catch {
    confirming.value = false
    error.value = 'No se pudo vaciar la conversación. Inténtalo de nuevo.'
  }
}
</script>

<template>
  <section class="tutor" data-testid="tutor-chat" aria-labelledby="tutor-title">
    <h2 id="tutor-title">Pregunta a tu tutor</h2>
    <p v-if="isMinor" class="muted" data-testid="tutor-minor-notice">Tu adulto tutor puede ver esta conversación.</p>

    <ol v-if="messages.length > 0" class="tutor-thread" aria-live="polite">
      <li v-for="m in messages" :key="m.id" class="tutor-msg" :class="m.role === 'USER' ? 'from-user' : 'from-tutor'" :data-testid="`tutor-msg-${m.id}`" :data-status="m.status">
        <p class="muted tutor-who">{{ m.role === 'USER' ? 'Tú' : 'Tutor' }}</p>
        <MarkdownContent v-if="m.role === 'TUTOR'" :source="m.content" />
        <p v-else class="statement">{{ m.content }}</p>
        <p v-if="sources(m).length > 0" class="muted" :data-testid="`tutor-sources-${m.id}`">
          <template v-for="(s, i) in sources(m)" :key="s.n"><template v-if="i > 0">, </template>Fuente {{ s.n }}<template v-if="s.page"> (página {{ s.page }})</template></template>
        </p>
      </li>
    </ol>
    <span ref="end"></span>

    <p v-if="sending" class="muted" role="status" data-testid="tutor-thinking">El tutor está pensando…</p>
    <p v-if="error" class="error" role="alert" data-testid="tutor-error">{{ error }}</p>

    <label for="tutor-input" class="visually-hidden">Tu pregunta</label>
    <textarea id="tutor-input" ref="input" v-model="question" rows="3" :maxlength="MAX_QUESTION" placeholder="Escribe tu duda sobre esta lección" data-testid="tutor-input" :disabled="sending"
              @keydown.enter.exact="onEnter"></textarea>
    <p class="actions">
      <button type="button" class="primary" data-testid="tutor-send" :disabled="sending" @click="send">Preguntar</button>
      <span class="muted" data-testid="tutor-count">{{ question.length }} / {{ MAX_QUESTION }}</span>
      <button v-if="canClear && messages.length > 0 && !confirming" type="button" data-testid="tutor-clear" @click="confirming = true">Vaciar la conversación</button>
    </p>

    <p v-if="confirming" class="actions" role="group" aria-label="Confirmar">
      <span>¿Borrar toda la conversación de esta lección?</span>
      <button type="button" class="primary" data-testid="tutor-clear-confirm" @click="clear">Sí, borrar</button>
      <button type="button" data-testid="tutor-clear-cancel" @click="confirming = false">No</button>
    </p>

    <p class="muted" data-testid="tutor-ai-notice">Texto generado por IA: el tutor puede equivocarse.</p>
  </section>
</template>
