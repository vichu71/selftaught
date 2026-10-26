<script setup lang="ts">
// `code` se acepta para que nadie tenga que quitarlo antes, pero NUNCA se muestra (AC-16).
defineProps<{ state: 'loading' | 'empty' | 'error'; message?: string; code?: string }>()
</script>

<template>
  <div class="ui-state" :class="`ui-state--${state}`" :role="state === 'error' ? 'alert' : 'status'">
    <p v-if="state === 'loading'" class="muted"><span class="spinner" aria-hidden="true"></span> {{ message || 'Cargando…' }}</p>
    <p v-else>{{ message }}</p>
    <slot name="action" />
  </div>
</template>
