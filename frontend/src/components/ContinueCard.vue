<script setup lang="ts">
import type { NextStop } from '@/map/layoutMap'

defineProps<{ stop: NextStop; courseTitle: string }>()
</script>

<template>
  <section class="continue-card" data-testid="continue-card" aria-labelledby="continue-title">
    <p class="ui-tag">Siguiente parada</p>
    <h2 id="continue-title">{{ stop.label }}</h2>
    <p>{{ courseTitle }} · {{ stop.topicTitle }}</p>
    <RouterLink :to="stop.route" class="ui-button ui-button--primary" data-testid="continue-link">Seguir leyendo →</RouterLink>
  </section>
</template>
