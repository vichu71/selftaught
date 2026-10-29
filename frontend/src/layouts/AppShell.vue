<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Landscape from '@/world/Landscape.vue'
import NavBar from './NavBar.vue'
import { zoneOf } from './zone'

const route = useRoute()
const zone = computed(() => zoneOf(route.path))
</script>

<template>
  <div class="shell" data-testid="shell-app" :data-zone="zone">
    <Landscape />
    <a href="#contenido" class="skip-link" data-testid="skip-link">Saltar al contenido</a>
    <NavBar />
    <main id="contenido" class="shell-main" tabindex="-1"><slot /></main>
  </div>
</template>
