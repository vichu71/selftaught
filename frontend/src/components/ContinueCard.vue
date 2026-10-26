<script setup lang="ts">
import type { NextStop } from '@/map/layoutMap'
import PlantIllustration from '@/world/PlantIllustration.vue'

defineProps<{ stop: NextStop; courseTitle: string }>()
</script>

<template>
  <section class="continue-card" data-testid="continue-card" aria-labelledby="continue-title">
    <PlantIllustration class="continue-plant" stage="sprout" />
    <div class="continue-body">
      <p class="ui-tag">Siguiente parada</p>
      <h2 id="continue-title">{{ stop.label }}</h2>
      <p>{{ courseTitle }} · {{ stop.topicTitle }}</p>
      <RouterLink :to="stop.route" class="ui-button ui-button--primary" data-testid="continue-link">Seguir leyendo →</RouterLink>
    </div>
  </section>
</template>
