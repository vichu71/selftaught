<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import AuthShell from '@/layouts/AuthShell.vue'

const route = useRoute()
</script>

<template>
  <AuthShell v-if="route.meta.public"><RouterView /></AuthShell>
  <AppShell v-else><RouterView /></AppShell>
</template>
