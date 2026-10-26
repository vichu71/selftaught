<script setup lang="ts">
import NavBar from './NavBar.vue'
</script>

<template>
  <div class="shell" data-testid="shell-app">
    <a href="#contenido" class="skip-link" data-testid="skip-link">Saltar al contenido</a>
    <NavBar />
    <main id="contenido" class="shell-main" tabindex="-1"><slot /></main>
  </div>
</template>
