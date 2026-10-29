<script setup lang="ts">
import { ref } from 'vue'
import { File as FileIcon, FileText, FileType } from 'lucide-vue-next'
import type { MaterialDocument } from '@/api/client'
import MaterialStatus from '@/components/MaterialStatus.vue'
import { reasonText as reason } from '@/material/reasons'

const props = defineProps<{ documents: MaterialDocument[]; readonly?: boolean; usage?: Record<string, { id: string; title: string }[]> | null }>()
const emit = defineEmits<{ remove: [id: string] }>()

const confirming = ref('')

const usedBy = (id: string) => props.usage?.[id] ?? []
const usageNote = (id: string) => {
  const n = usedBy(id).length
  return n > 0 ? `Se usa en ${n} ${n === 1 ? 'curso' : 'cursos'}. Los cursos ya creados no cambian.` : ''
}

const typeLabels = { PDF: 'PDF', DOCX: 'Word', TEXT: 'Texto' } as const
const icons = { PDF: FileText, DOCX: FileType, TEXT: FileIcon } as const

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
    <li v-for="d in documents" :key="d.id" class="document doc-card" :class="{ 'is-confirming': confirming === d.id }" :data-testid="`doc-${d.id}`">
      <component :is="icons[d.mediaType] ?? FileIcon" :size="28" class="doc-card-icon" aria-hidden="true" :data-kind="d.mediaType" :data-testid="`doc-icon-${d.id}`" />
      <div class="document-main">
        <strong class="document-name">{{ d.name }}</strong>
        <span class="badge">{{ typeLabels[d.mediaType] }} · {{ size(d.sizeBytes) }}</span>
        <MaterialStatus :status="d.status" :data-testid="`doc-status-${d.id}`" />
        <template v-if="usage && !readonly && d.ownedByMe">
          <span v-if="usedBy(d.id).length > 0" class="doc-usage" :data-testid="`doc-usage-${d.id}`">
            Usado en:
            <RouterLink v-for="c in usedBy(d.id)" :key="c.id" :to="`/cursos/${c.id}`" class="ui-tag ui-tag--map usage-chip" :data-testid="`doc-usage-chip-${d.id}-${c.id}`">{{ c.title }}</RouterLink>
          </span>
          <span v-else class="doc-usage muted" :data-testid="`doc-unused-${d.id}`">Sin usar todavía</span>
        </template>
        <p v-if="d.status === 'ERROR'" class="field-error" :data-testid="`doc-error-${d.id}`">{{ reason(d.errorKind) }}</p>
      </div>

      <div v-if="!readonly && d.ownedByMe" class="actions">
        <button v-if="confirming !== d.id" type="button" :data-testid="`delete-${d.id}`" @click="confirming = d.id">Borrar</button>
        <template v-else>
          <span v-if="usageNote(d.id)" class="muted" :data-testid="`delete-usage-note-${d.id}`">{{ usageNote(d.id) }}</span>
          <span class="muted">¿Borrar este documento?</span>
          <button type="button" class="danger" :data-testid="`confirm-delete-${d.id}`" @click="confirmRemove(d.id)">Sí, borrar</button>
          <button type="button" :data-testid="`cancel-delete-${d.id}`" @click="confirming = ''">Cancelar</button>
        </template>
      </div>
    </li>
  </ul>
</template>
