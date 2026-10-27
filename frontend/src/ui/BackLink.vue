<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'

// «Volver»: un enlace con forma de botón (flecha y texto), el mismo en todas las pantallas. El data-testid lo pone quien lo usa.
withDefaults(defineProps<{ to?: string; label?: string; ariaLabel?: string }>(), { to: '/', label: 'Inicio', ariaLabel: 'Volver al inicio' })
</script>

<template>
  <RouterLink :to="to" class="back-link" :aria-label="ariaLabel">
    <ArrowLeft :size="18" aria-hidden="true" />
    <span>{{ label }}</span>
  </RouterLink>
</template>
