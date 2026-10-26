<script setup lang="ts">
defineProps<{ x: number; y: number }>()
</script>

<template>
  <g data-testid="map-wanderer" aria-hidden="true" :transform="`translate(${x} ${y})`">
    <circle class="wd-head" cx="0" cy="-16" r="7" />
    <path class="wd-body" d="M-8 6 Q0 -10 8 6 Z" />
  </g>
</template>
