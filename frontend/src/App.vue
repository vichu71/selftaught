<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import AuthShell from '@/layouts/AuthShell.vue'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()
</script>

<template>
  <AuthShell v-if="route.meta.public"><RouterView /></AuthShell>
  <AppShell v-else><RouterView :key="ui.restKey" /></AppShell>
</template>
