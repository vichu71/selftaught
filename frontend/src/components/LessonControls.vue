<script setup lang="ts">
import { ref } from 'vue'
import { api } from '@/api/client'
import Celebration from '@/ui/Celebration.vue'

const props = defineProps<{ courseId: string; pieceId: string; done: boolean; navigation?: { previous?: string; next?: string } }>()

const isDone = ref(props.done)
const celebrate = ref(false) // solo al marcarla como hecha en esta pantalla, nunca al abrir una lección ya hecha
const busy = ref(false)
const error = ref(false)

async function toggle() {
  if (busy.value) return
  busy.value = true
  error.value = false
  try {
    await api.setLessonDone(props.courseId, props.pieceId, !isDone.value)
    isDone.value = !isDone.value
    celebrate.value = isDone.value
  } catch {
    error.value = true // el estado anterior se conserva
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="lesson-controls">
    <button type="button" class="primary" data-testid="mark-done" :aria-pressed="isDone ? 'true' : 'false'" :disabled="busy" @click="toggle">
      {{ isDone ? '✓ Hecha (pulsa para desmarcar)' : 'Marcar como hecha' }}
    </button>
    <Celebration v-if="celebrate" data-testid="celebration" />
    <p v-if="error" class="error" role="alert" data-testid="done-error">No se pudo guardar. Inténtalo de nuevo.</p>

    <nav class="nav" aria-label="Lecciones">
      <RouterLink v-if="navigation?.previous" :to="`/cursos/${courseId}/piezas/${navigation.previous}`" data-testid="nav-previous">← Lección anterior</RouterLink>
      <span v-else></span>
      <RouterLink v-if="navigation?.next" :to="`/cursos/${courseId}/piezas/${navigation.next}`" data-testid="nav-next">Lección siguiente →</RouterLink>
    </nav>
  </div>
</template>
