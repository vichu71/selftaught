<script setup lang="ts">
import type { Exercise } from '@/api/client'

defineProps<{ exercises: Exercise[] }>()

const difficulty: Record<Exercise['difficulty'], string> = { LOW: 'Fácil', MEDIUM: 'Normal', HIGH: 'Difícil' }
</script>

<template>
  <ol class="exercises" data-testid="exercises">
    <li v-for="(exercise, i) in exercises" :key="i" class="exercise" :data-testid="`exercise-${i}`">
      <p class="muted">{{ difficulty[exercise.difficulty] ?? '' }}</p>
      <p class="statement">{{ exercise.statement }}</p>
      <details>
        <summary>Ver solución</summary>
        <p class="statement"><strong>Solución:</strong> {{ exercise.solution }}</p>
        <p v-if="exercise.explanation" class="statement">{{ exercise.explanation }}</p>
      </details>
    </li>
  </ol>
</template>
