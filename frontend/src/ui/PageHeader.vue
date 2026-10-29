<script setup lang="ts">
import type { Component } from 'vue'

// Cabecera de página (F17): burbuja con el icono y el color de la zona, el título (y lo que le acompañe) y, aparte, acciones.
defineProps<{ icon?: Component }>()
</script>

<template>
  <header class="topbar page-header" data-testid="page-header">
    <span v-if="icon" class="page-bubble" aria-hidden="true"><component :is="icon" :size="26" /></span>
    <div class="page-title"><slot /></div>
    <div v-if="$slots.actions" class="page-actions"><slot name="actions" /></div>
  </header>
</template>
