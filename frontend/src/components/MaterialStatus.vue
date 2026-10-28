<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Loader, TriangleAlert } from 'lucide-vue-next'
import type { MaterialStatus } from '@/api/client'

const props = defineProps<{ status: MaterialStatus }>()

const labels: Record<MaterialStatus, string> = {
  UPLOADED: 'Subido',
  PROCESSING: 'Procesando',
  READY: 'Listo',
  ERROR: 'Error',
}

const label = computed(() => labels[props.status] ?? 'Desconocido')
</script>

<template>
  <span class="status" :class="`status-${status.toLowerCase()}`">
    <CircleCheck v-if="status === 'READY'" :size="14" aria-hidden="true" />
    <TriangleAlert v-else-if="status === 'ERROR'" :size="14" aria-hidden="true" />
    <Loader v-else :size="14" aria-hidden="true" />
    {{ label }}
  </span>
</template>
