<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, api, type CourseDetail } from '@/api/client'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { BLOCKED_TEXT } from '@/stores/course'

const props = defineProps<{ courseId: string; pieceId: string }>()
const emit = defineEmits<{ saved: [course: CourseDetail]; cancel: [] }>()

const MAX = 20000

const text = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const errorMessages: Record<string, string> = {
  COURSE_BUSY: 'El curso se está generando. Espera a que termine para editarlo.',
  EDIT_NOT_ALLOWED: BLOCKED_TEXT,
}

onMounted(async () => {
  try {
    text.value = (await api.getPiece(props.courseId, props.pieceId)).markdown ?? ''
  } catch {
    error.value = 'No se pudo cargar la lección.'
  } finally {
    loading.value = false
  }
})

async function save() {
  if (saving.value) return
  error.value = ''
  if (text.value.trim() === '') {
    error.value = 'Escribe el texto de la lección.'
    return
  }
  saving.value = true
  try {
    emit('saved', await api.editLesson(props.courseId, props.pieceId, text.value))
  } catch (e) {
    error.value = (e instanceof ApiError && e.code && errorMessages[e.code]) || 'No se pudo guardar la lección. Inténtalo de nuevo.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="card" data-testid="lesson-editor">
    <h3>Texto de la lección</h3>
    <p v-if="loading" class="muted">Cargando…</p>
    <template v-else>
      <label for="lesson-text" class="visually-hidden">Texto de la lección en Markdown</label>
      <textarea id="lesson-text" v-model="text" rows="14" :maxlength="MAX" data-testid="lesson-text"></textarea>
      <p class="muted">{{ text.length }} / {{ MAX }}. Al guardar, sus ejercicios y el examen del tema se quedan como están y se marcan como desactualizados.</p>
      <h4>Vista previa</h4>
      <div data-testid="lesson-preview"><MarkdownContent :source="text" /></div>
      <p v-if="error" class="error" role="alert" data-testid="lesson-error">{{ error }}</p>
      <p class="actions">
        <button type="button" class="primary" data-testid="lesson-save" :disabled="saving" @click="save">Guardar la lección</button>
        <button type="button" data-testid="lesson-cancel" @click="emit('cancel')">Cancelar</button>
      </p>
    </template>
  </section>
</template>
