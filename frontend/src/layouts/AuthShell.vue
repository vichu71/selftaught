<script setup lang="ts">
import Mascot from '@/world/Mascot.vue'
import Landscape from '@/world/Landscape.vue'
</script>

<template>
  <div class="auth-shell" data-testid="shell-auth">
    <Landscape />
    <a href="#contenido" class="skip-link">Saltar al contenido</a>
    <span class="brand"><Mascot class="brand-mascot" />Study Lab</span>
    <main id="contenido" tabindex="-1"><slot /></main>
  </div>
</template>
