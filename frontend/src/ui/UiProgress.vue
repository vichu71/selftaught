<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: number; label: string }>()
const clamped = computed(() => Math.min(100, Math.max(0, Math.round(props.value))))
</script>

<template>
  <div class="ui-progress">
    <div class="ui-progress-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="clamped" :aria-label="label">
      <div class="ui-progress-bar" :style="{ width: `${clamped}%` }"></div>
    </div>
    <span class="ui-progress-text" aria-hidden="true">{{ clamped }} %</span>
  </div>
</template>
