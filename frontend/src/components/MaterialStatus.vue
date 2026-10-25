<script setup lang="ts">
import { computed } from 'vue'
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
  <span class="status" :class="`status-${status.toLowerCase()}`">{{ label }}</span>
</template>
