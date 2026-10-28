<script setup lang="ts">
import { computed, ref } from 'vue'
import { Upload } from 'lucide-vue-next'

// La zona de subida es la etiqueta del <input type="file"> real, que sigue siendo el único control (teclado, lector de pantalla,
// selector del sistema) y solo se oculta visualmente. Arrastrar y soltar entrega el PRIMER fichero a la misma subida: no hay otro camino.
const props = defineProps<{ disabled?: boolean; uploading?: boolean; minor?: boolean }>()
const emit = defineEmits<{ choose: [file: File] }>()

const over = ref(false)
const note = ref('')
const off = computed(() => Boolean(props.disabled || props.uploading))

function onChange(event: Event) {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  note.value = ''
  if (file) emit('choose', file)
  element.value = '' // permite volver a elegir el mismo fichero
}

function onOver() {
  if (!off.value) over.value = true
}

function onDrop(event: DragEvent) {
  over.value = false
  if (off.value) return
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length === 0) return
  note.value = files.length > 1 ? 'Se sube un fichero cada vez: he cogido el primero.' : ''
  emit('choose', files[0])
}
</script>

<template>
  <div
    class="drop-zone"
    :class="{ 'is-over': over, 'is-disabled': off }"
    :aria-disabled="off ? 'true' : 'false'"
    data-testid="drop-zone"
    @dragenter.prevent="onOver"
    @dragover.prevent="onOver"
    @dragleave="over = false"
    @drop.prevent="onDrop"
  >
    <Upload :size="32" aria-hidden="true" />
    <p class="drop-zone-title">Arrastra un documento aquí o</p>
    <label for="material-file" class="ui-button ui-button--primary drop-zone-button">Elegir fichero</label>
    <input
      id="material-file" type="file" class="visually-hidden" accept=".pdf,.docx,.txt,.md" data-testid="file-input"
      :disabled="off" @change="onChange"
    />
    <p class="muted drop-zone-hint">PDF, Word (.docx) o texto (.txt, .md) · máximo 10 MB. Solo tú lo ves{{ minor ? ' y tu tutor' : '' }}.</p>
    <p v-if="uploading" class="drop-zone-state" role="status" data-testid="uploading">Subiendo…</p>
    <p v-if="note" class="drop-zone-state" role="status">{{ note }}</p>
  </div>
</template>
