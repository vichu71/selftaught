<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'

const route = useRoute()
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  if (loading.value) return
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    error.value = 'El enlace no es válido. Pide uno nuevo.'
    return
  }
  if (newPassword.value.length < 10 || newPassword.value.length > 128) {
    error.value = 'La contraseña debe tener entre 10 y 128 caracteres.'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Las contraseñas no coinciden.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await api.resetPassword(token, newPassword.value)
    done.value = true
  } catch {
    error.value = 'El enlace no es válido o ha caducado. Pide uno nuevo.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1>Elegir contraseña nueva</h1>

    <section v-if="done" class="card" data-testid="done">
      <p class="success">Contraseña cambiada. Se han cerrado tus otras sesiones.</p>
      <RouterLink to="/login">Entrar</RouterLink>
    </section>

    <form v-else class="card" novalidate @submit.prevent="submit">
      <label for="new-password">Contraseña nueva (mínimo 10 caracteres)</label>
      <input id="new-password" v-model="newPassword" data-testid="new-password" type="password" autocomplete="new-password" />

      <label for="confirm-password">Repite la contraseña</label>
      <input id="confirm-password" v-model="confirmPassword" data-testid="confirm-password" type="password" autocomplete="new-password" />

      <p v-if="error" class="error" role="alert" data-testid="error">{{ error }}</p>
      <button class="primary" type="submit" data-testid="submit" :disabled="loading">Cambiar contraseña</button>
      <div class="links"><RouterLink to="/forgot-password">Pedir otro enlace</RouterLink></div>
    </form>
  </div>
</template>
