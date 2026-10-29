<script setup lang="ts">
import type { PlantStage } from './plantStage'

defineProps<{ stage: PlantStage; label?: string }>()
</script>

<template>
  <svg
    class="plant"
    :class="{ 'plant--bloom': stage === 'fruit' }"
    viewBox="0 0 120 120"
    xmlns="http://www.w3.org/2000/svg"
    :data-stage="stage"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
  >
    <ellipse class="pl-soil" cx="60" cy="104" rx="42" ry="11" />
    <template v-if="stage === 'seed'">
      <ellipse class="pl-trunk" cx="60" cy="96" rx="9" ry="6" />
      <path class="pl-glint" d="M36 82 l5 5 M84 82 l-5 5 M60 72 v7" />
    </template>
    <template v-else-if="stage === 'sprout'">
      <path class="pl-stem" d="M60 104 V66" />
      <ellipse class="pl-leaf" cx="46" cy="66" rx="16" ry="8" transform="rotate(-28 46 66)" />
      <ellipse class="pl-leaf pl-leaf--b" cx="76" cy="58" rx="16" ry="8" transform="rotate(28 76 58)" />
    </template>
    <template v-else>
      <rect class="pl-trunk" x="53" y="56" width="14" height="50" rx="6" />
      <circle class="pl-leaf" cx="60" cy="38" r="26" />
      <circle class="pl-leaf pl-leaf--b" cx="38" cy="54" r="17" />
      <circle class="pl-leaf pl-leaf--b" cx="82" cy="54" r="17" />
      <template v-if="stage === 'fruit'">
        <circle class="pl-fruit" data-part="fruit" cx="46" cy="40" r="5" />
        <circle class="pl-fruit" data-part="fruit" cx="72" cy="30" r="5" />
        <circle class="pl-fruit" data-part="fruit" cx="78" cy="56" r="5" />
        <circle class="pl-fruit" data-part="fruit" cx="38" cy="58" r="5" />
      </template>
    </template>
  </svg>
</template>
