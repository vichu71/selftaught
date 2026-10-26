<script setup lang="ts">
import { computed } from 'vue'
import Wanderer from '@/world/Wanderer.vue'
import type { Station } from './layoutMap'

const props = defineProps<{ station: Station; narrow?: boolean }>()

const radius = computed(() => (props.station.state === 'now' ? 26 : 20))
const classes = computed(() => ['map-station', `map-station--${props.station.state}`])

/** El nombre se parte en líneas (dos en ancho, una en estrecho); lo que no cabe se corta con «…» (el texto completo está en la lista y en el título). */
function wrap(text: string, max: number, maxLines: number): string[] {
  const lines: string[] = []
  let current = ''
  for (const word of text.split(/\s+/).filter(Boolean)) {
    const next = current ? `${current} ${word}` : word
    if (next.length <= max) current = next
    else {
      if (current) lines.push(current)
      current = word
    }
  }
  if (current) lines.push(current)
  const shown = lines.slice(0, maxLines).map((l) => (l.length > max ? `${l.slice(0, max - 1)}…` : l))
  if (lines.length > maxLines) shown[maxLines - 1] = `${shown[maxLines - 1].replace(/…$/, '').slice(0, max - 1)}…`
  return shown
}

const lines = computed(() => wrap(props.station.label, props.narrow ? 26 : 16, props.narrow ? 1 : 2))
</script>

<template>
  <g>
    <title>{{ station.label }}</title>
    <circle v-if="station.kind === 'lesson'" data-kind="lesson" :class="classes" :cx="station.x" :cy="station.y" :r="radius" />
    <rect v-else data-kind="exam" :class="classes" :x="station.x - radius" :y="station.y - radius" :width="radius * 2" :height="radius * 2" rx="8" />
    <text v-if="narrow" class="map-label map-label--side" :x="station.x + radius + 14" :y="station.y - 4">{{ lines[0] }}</text>
    <text v-else class="map-label" :x="station.x" :y="station.y + radius + 18">
      <tspan v-for="(line, i) in lines" :key="i" :x="station.x" :dy="i === 0 ? 0 : 16">{{ line }}</tspan>
    </text>
    <template v-if="station.state === 'now'">
      <Wanderer :x="narrow ? station.x : station.x + radius + 22" :y="narrow ? station.y - radius - 4 : station.y + 8" />
      <template v-if="narrow">
        <rect class="map-here" :x="station.x + radius + 14" :y="station.y + 4" width="132" height="24" rx="12" />
        <text class="map-here-text" :x="station.x + radius + 80" :y="station.y + 21">TÚ ESTÁS AQUÍ</text>
      </template>
      <template v-else>
        <rect class="map-here" :x="station.x - 66" :y="station.y - radius - 40" width="132" height="26" rx="13" />
        <text class="map-here-text" :x="station.x" :y="station.y - radius - 22">TÚ ESTÁS AQUÍ</text>
      </template>
    </template>
  </g>
</template>
