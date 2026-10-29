<script setup lang="ts">
import { File as FileIcon, FileText, FileType } from 'lucide-vue-next'

// «Material de este curso» (F18): los documentos que se enlazaron al crearlo. Nombres siempre como texto; sin documentos no se pinta nada.
defineProps<{ documents: { id: string; name: string }[] }>()

function iconFor(name: string) {
  const extension = name.includes('.') ? name.split('.').pop()!.toLowerCase() : ''
  return extension === 'pdf' ? FileText : extension === 'docx' ? FileType : FileIcon
}
</script>

<template>
  <section v-if="documents.length > 0" class="card course-material" data-testid="course-material">
    <h2>Material de este curso</h2>
    <ul class="course-material-list">
      <li v-for="d in documents" :key="d.id" class="course-material-item" :data-testid="`course-material-item-${d.id}`">
        <component :is="iconFor(d.name)" :size="22" aria-hidden="true" />
        <span>{{ d.name }}</span>
      </li>
    </ul>
  </section>
</template>
