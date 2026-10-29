<script setup lang="ts">
// Celebración corta (F17, ADR-97): el texto siempre sale; el confeti es decoración CSS de una pasada que solo se ve con movimiento permitido.
withDefaults(defineProps<{ message?: string }>(), { message: '¡Hecho!' })
</script>

<template>
  <div class="celebration">
    <div class="confetti" aria-hidden="true">
      <span v-for="n in 12" :key="n" class="confetti-piece" :style="{ '--i': n }"></span>
    </div>
    <p class="celebration-text" role="status">{{ message }}</p>
  </div>
</template>
