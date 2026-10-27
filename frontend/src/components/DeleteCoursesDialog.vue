<script setup lang="ts">
import { computed } from 'vue'
import { exportPath } from '@/api/client'
import UiDialog from '@/ui/UiDialog.vue'

const props = defineProps<{ modelValue: boolean; courses: { id: string; title: string }[]; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [open: boolean]; confirm: [] }>()

const many = computed(() => props.courses.length > 1)
const title = computed(() => (many.value ? `Borrar ${props.courses.length} cursos` : 'Borrar curso'))
</script>

<template>
  <UiDialog
    :model-value="modelValue"
    :title="title"
    :busy="busy"
    tone="danger"
    description="Esta acción no se puede deshacer."
    confirm-label="Borrar definitivamente"
    cancel-label="Cancelar"
    confirm-testid="delete-courses-confirm"
    cancel-testid="delete-courses-cancel"
    data-testid="delete-courses-dialog"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @confirm="emit('confirm')"
  >
    <p>{{ many ? `Vas a borrar ${courses.length} cursos:` : 'Vas a borrar este curso:' }}</p>
    <ul data-testid="delete-courses-titles">
      <li v-for="c in courses" :key="c.id">
        {{ c.title }}
        <a :href="exportPath(c.id)" download :data-testid="`delete-courses-export-${c.id}`">Exportar antes</a>
      </li>
    </ul>
    <p><strong>Se pierde para siempre:</strong> las lecciones, los ejercicios y exámenes, tus intentos, las conversaciones con el tutor, las ilustraciones y los códigos de compartir.</p>
    <p><strong>Se conserva:</strong> tu gasto de IA, tu material subido y las copias que otras personas ya hicieron de tus cursos.</p>
  </UiDialog>
</template>
