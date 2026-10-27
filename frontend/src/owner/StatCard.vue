<script setup lang="ts">
import { CircleCheck, OctagonX, TriangleAlert } from 'lucide-vue-next'

// Una cifra con su etiqueta. Si lleva un estado (`tone`), se dice también con un icono y con palabras (`status`): nunca solo con color.
withDefaults(defineProps<{ label: string; value: string; hint?: string; tone?: 'ok' | 'warn' | 'bad' | ''; status?: string }>(), { hint: '', tone: '', status: '' })
</script>

<template>
  <div class="owner-stat" :class="tone ? `owner-stat--${tone}` : ''">
    <p class="owner-stat-label">{{ label }}</p>
    <p class="owner-stat-value">{{ value }}</p>
    <p v-if="tone" class="owner-stat-status" data-testid="stat-status">
      <CircleCheck v-if="tone === 'ok'" :size="16" aria-hidden="true" />
      <TriangleAlert v-else-if="tone === 'warn'" :size="16" aria-hidden="true" />
      <OctagonX v-else :size="16" aria-hidden="true" />
      <span>{{ status }}</span>
    </p>
    <p v-if="hint" class="owner-stat-hint muted">{{ hint }}</p>
  </div>
</template>
