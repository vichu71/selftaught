<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'plain' | 'now' | 'ok' | 'accent' }>(), { tone: 'plain' })
</script>

<template>
  <span class="ui-tag" :class="`ui-tag--${tone}`"><slot /></span>
</template>
