<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const identifier = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  if (loading.value) return
  const user = identifier.value.trim()
  if (!user || !password.value) {
    error.value = 'Escribe tu correo o usuario y tu contraseña.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login(user, password.value)
    await router.push('/')
  } catch (e) {
    // Mensaje genérico: nunca se dice si la cuenta existe
    error.value =
      e instanceof ApiError && e.status === 429
        ? 'Demasiados intentos. Espera unos minutos y vuelve a probar.'
        : e instanceof ApiError && e.status === 401
          ? 'Correo, usuario o contraseña incorrectos.'
          : 'No se pudo iniciar sesión. Inténtalo de nuevo.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page">
    <h1>Entrar en Study Lab</h1>
    <form class="card" novalidate @submit.prevent="submit">
      <label for="identifier">Correo o usuario</label>
      <input id="identifier" v-model="identifier" data-testid="identifier" autocomplete="username" autocapitalize="none" />

      <label for="password">Contraseña</label>
      <input id="password" v-model="password" data-testid="password" type="password" autocomplete="current-password" />

      <p v-if="error" class="error" role="alert" data-testid="error">{{ error }}</p>

      <button class="primary" type="submit" data-testid="submit" :disabled="loading">
        {{ loading ? 'Entrando…' : 'Entrar' }}
      </button>

      <div class="links">
        <RouterLink to="/forgot-password">¿Has olvidado la contraseña?</RouterLink>
        <RouterLink to="/register">Crear cuenta de adulto</RouterLink>
      </div>
    </form>
  </main>
</template>
