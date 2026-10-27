<script setup lang="ts">
import { computed } from 'vue'
import { withUnit } from './format'

// Reparto en barras horizontales: cada barra va con su etiqueta y su valor escritos, así que la lista ya es su propia tabla.
const props = defineProps<{ items: { label: string; value: number }[]; title: string; unit?: string }>()

const biggest = computed(() => Math.max(0, ...props.items.map((i) => (Number.isFinite(i.value) ? i.value : 0))))
const width = (value: number) => (biggest.value > 0 && Number.isFinite(value) ? Math.min(100, Math.round((value / biggest.value) * 1000) / 10) : 0)
</script>

<template>
  <section class="ui-bars">
    <h3>{{ title }}</h3>
    <p v-if="items.length === 0" class="muted" data-testid="chart-empty">Sin datos en este rango.</p>
    <ul v-else>
      <li v-for="(item, i) in items" :key="i" data-testid="bar-item">
        <span class="ui-bar-label">{{ item.label }}</span>
        <span class="ui-bar-track" aria-hidden="true"><span class="ui-bar-fill" :class="`ui-series-${(i % 4) + 1}`" data-testid="bar-fill" :style="{ width: `${width(item.value)}%` }"></span></span>
        <span class="ui-bar-value">{{ withUnit(item.value, unit) }}</span>
      </li>
    </ul>
  </section>
</template>
