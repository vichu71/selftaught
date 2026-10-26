<script setup lang="ts">
import { computed } from 'vue'
import type { Station } from './layoutMap'

const props = defineProps<{ station: Station }>()

const radius = computed(() => (props.station.state === 'now' ? 26 : 20))
const short = computed(() => (props.station.label.length > 18 ? `${props.station.label.slice(0, 17)}…` : props.station.label))
const classes = computed(() => ['map-station', `map-station--${props.station.state}`])
</script>

<template>
  <g>
    <title>{{ station.label }}</title>
    <circle v-if="station.kind === 'lesson'" data-kind="lesson" :class="classes" :cx="station.x" :cy="station.y" :r="radius" />
    <rect v-else data-kind="exam" :class="classes" :x="station.x - radius" :y="station.y - radius" :width="radius * 2" :height="radius * 2" rx="8" />
    <text class="map-label" :x="station.x" :y="station.y + radius + 18">{{ short }}</text>
    <template v-if="station.state === 'now'">
      <rect class="map-here" :x="station.x - 66" :y="station.y - radius - 40" width="132" height="26" rx="7" />
      <text class="map-here-text" :x="station.x" :y="station.y - radius - 22">TÚ ESTÁS AQUÍ</text>
    </template>
  </g>
</template>
