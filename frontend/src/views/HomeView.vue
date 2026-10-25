<script setup lang="ts">
import { useRouter } from 'vue-router'
import StudentsPanel from '@/components/StudentsPanel.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout().catch(() => undefined)
  await router.push('/login')
}
</script>

<template>
  <main class="page wide" data-testid="app-shell">
    <header class="topbar">
      <h1 data-testid="welcome">Hola, {{ auth.user?.displayName }}</h1>
      <button type="button" data-testid="logout" @click="logout">Cerrar sesión</button>
    </header>

    <p v-if="auth.user?.kind === 'MINOR'" class="notice" data-testid="guardian-notice">
      Tu adulto tutor puede ver tus cursos, tu progreso y tus conversaciones con el tutor de IA.
    </p>

    <section class="card">
      <h2>Tu espacio de estudio</h2>
      <p class="muted">Aquí aparecerán tus cursos.</p>
      <p><RouterLink to="/material" data-testid="material-link">Mi material</RouterLink> · sube tus apuntes (PDF, Word o texto).</p>
    </section>

    <StudentsPanel v-if="auth.isTutor" />
  </main>
</template>
