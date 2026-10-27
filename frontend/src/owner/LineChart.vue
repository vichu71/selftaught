<script setup lang="ts">
import { computed } from 'vue'
import { withUnit } from '@/ui/format'

// Serie diaria dibujada con elementos SVG (sin librería ni colores a mano: todo por clases que leen tokens) y, debajo, la misma serie como tabla.
const props = defineProps<{ points: { label: string; value: number }[]; title: string; unit?: string }>()

const W = 600
const H = 180
const PAD = 12

const values = computed(() => props.points.map((p) => (Number.isFinite(p.value) ? p.value : 0)))
const top = computed(() => Math.max(0, ...values.value))
const xs = computed(() => values.value.map((_, i) => (values.value.length === 1 ? W / 2 : PAD + (i * (W - 2 * PAD)) / (values.value.length - 1))))
const ys = computed(() => values.value.map((v) => (top.value > 0 ? H - PAD - (v / top.value) * (H - 2 * PAD) : H - PAD)))
const path = computed(() => xs.value.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${ys.value[i].toFixed(1)}`).join(' '))
const best = computed(() => {
  let index = 0
  values.value.forEach((v, i) => { if (v > values.value[index]) index = i })
  return index
})
</script>

<template>
  <figure class="owner-chart">
    <figcaption><h3>{{ title }}</h3></figcaption>
    <p v-if="points.length === 0" class="muted" data-testid="chart-empty">Sin datos en este rango.</p>
    <template v-else>
      <svg class="owner-line-svg" :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`${title}: ${points.length} días, máximo ${withUnit(top, unit)}`" preserveAspectRatio="none">
        <line class="owner-axis" :x1="PAD" :y1="H - PAD" :x2="W - PAD" :y2="H - PAD" />
        <path class="owner-line" data-testid="line-path" :d="path" />
        <circle v-for="(x, i) in xs" :key="i" class="owner-dot" :cx="x" :cy="ys[i]" r="3" />
      </svg>
      <p class="owner-chart-summary" data-testid="chart-summary">Máximo: {{ withUnit(values[best], unit) }} ({{ points[best].label }})</p>
      <details class="owner-chart-table">
        <summary>Ver tabla</summary>
        <table class="owner-table" data-testid="chart-table">
          <caption>{{ title }}</caption>
          <thead><tr><th scope="col">Día</th><th scope="col">Valor</th></tr></thead>
          <tbody>
            <tr v-for="(p, i) in points" :key="i"><td>{{ p.label }}</td><td>{{ withUnit(values[i], unit) }}</td></tr>
          </tbody>
        </table>
      </details>
    </template>
  </figure>
</template>
