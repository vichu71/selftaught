<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'warning' | 'error' }>(), { tone: 'info' })
</script>

<template>
  <div class="ui-notice" :class="`ui-notice--${tone}`" :role="tone === 'info' ? 'status' : 'alert'"><slot /></div>
</template>
