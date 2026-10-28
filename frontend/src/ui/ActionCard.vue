<script setup lang="ts">
import type { Component } from 'vue'

// Un atajo: una tarjeta que es el enlace entero, con icono, título y una línea de descripción. El data-testid lo pone quien lo usa.
defineProps<{ to: string; title: string; description: string; icon: Component }>()
</script>

<template>
  <RouterLink :to="to" class="action-card">
    <component :is="icon" :size="28" aria-hidden="true" />
    <span class="action-card-text">
      <strong>{{ title }}</strong>
      <span>{{ description }}</span>
    </span>
  </RouterLink>
</template>
