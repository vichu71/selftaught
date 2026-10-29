<script setup lang="ts">
import type { Component } from 'vue'
import type { Zone } from '@/layouts/zone'

// Un atajo: una tarjeta que es el enlace entero, con icono, título y una línea de descripción. El data-testid lo pone quien lo usa.
withDefaults(defineProps<{ to: string; title: string; description: string; icon: Component; zone?: Zone }>(), { zone: 'map' })
</script>

<template>
  <RouterLink :to="to" class="action-card" :data-zone="zone">
    <span class="action-card-bubble" aria-hidden="true"><component :is="icon" :size="26" /></span>
    <span class="action-card-text">
      <strong>{{ title }}</strong>
      <span>{{ description }}</span>
    </span>
  </RouterLink>
</template>
