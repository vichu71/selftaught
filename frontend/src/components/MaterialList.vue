<script setup lang="ts">
import { ref } from 'vue'
import type { MaterialDocument } from '@/api/client'
import MaterialStatus from '@/components/MaterialStatus.vue'

defineProps<{ documents: MaterialDocument[]; readonly?: boolean }>()
const emit = defineEmits<{ remove: [id: string] }>()

const confirming = ref('')

const reasons: Record<string, string> = {
  NO_TEXT: 'No contiene texto: los documentos escaneados no se admiten.',
  ENCRYPTED: 'Está protegido con contraseña.',
  CORRUPT: 'El fichero está dañado o no se puede leer.',
  TOO_MANY_PAGES: 'Tiene demasiadas páginas.',
  TOO_LARGE: 'Es demasiado grande una vez descomprimido.',
  TOO_MUCH_TEXT: 'Contiene demasiado texto.',
  INDEXING_UNAVAILABLE: 'El servicio de indexado no está disponible ahora. Bórralo y vuelve a subirlo más tarde.',
}

function reason(kind?: string): string {
  return (kind && reasons[kind]) || 'No se pudo procesar el documento.'
}

const typeLabels = { PDF: 'PDF', DOCX: 'Word', TEXT: 'Texto' } as const

function size(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

function confirmRemove(id: string) {
  confirming.value = ''
  emit('remove', id)
}
</script>

<template>
  <ul class="documents">
    <li v-for="d in documents" :key="d.id" class="document" :data-testid="`doc-${d.id}`">
      <div class="document-main">
        <strong class="document-name">{{ d.name }}</strong>
        <span class="badge">{{ typeLabels[d.mediaType] }} · {{ size(d.sizeBytes) }}</span>
        <MaterialStatus :status="d.status" :data-testid="`doc-status-${d.id}`" />
        <p v-if="d.status === 'ERROR'" class="field-error" :data-testid="`doc-error-${d.id}`">{{ reason(d.errorKind) }}</p>
      </div>

      <div v-if="!readonly && d.ownedByMe" class="actions">
        <button v-if="confirming !== d.id" type="button" :data-testid="`delete-${d.id}`" @click="confirming = d.id">Borrar</button>
        <template v-else>
          <span class="muted">¿Borrar este documento?</span>
          <button type="button" class="danger" :data-testid="`confirm-delete-${d.id}`" @click="confirmRemove(d.id)">Sí, borrar</button>
          <button type="button" :data-testid="`cancel-delete-${d.id}`" @click="confirming = ''">Cancelar</button>
        </template>
      </div>
    </li>
  </ul>
</template>
