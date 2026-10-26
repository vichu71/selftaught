<script setup lang="ts">
defineProps<{ label?: string }>()
</script>

<template>
  <svg
    class="mascot"
    viewBox="0 0 40 40"
    xmlns="http://www.w3.org/2000/svg"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
  >
    <circle class="mascot-body" cx="20" cy="20" r="18" />
    <circle class="mascot-eye" cx="14" cy="17" r="3.5" />
    <circle class="mascot-eye" cx="26" cy="17" r="3.5" />
    <circle class="mascot-pupil" cx="15" cy="17.5" r="1.7" />
    <circle class="mascot-pupil" cx="27" cy="17.5" r="1.7" />
    <path class="mascot-mouth" d="M13 25 Q20 31 27 25" />
  </svg>
</template>
