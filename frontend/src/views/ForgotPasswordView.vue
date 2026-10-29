<script setup lang="ts">
import BackLink from '@/ui/BackLink.vue'
import { ref } from 'vue'
import { api } from '@/api/client'

const email = ref('')
const loading = ref(false)
const sent = ref(false)
const error = ref('')

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

async function submit() {
  if (loading.value) return
  const value = email.value.trim()
  if (!EMAIL.test(value)) {
    error.value = 'Escribe un correo válido.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await api.forgotPassword(value)
    sent.value = true // la respuesta es la misma exista o no la cuenta
  } catch {
    error.value = 'No se pudo enviar. Inténtalo más tarde.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1>Recuperar contraseña</h1>

    <section v-if="sent" class="card" data-testid="sent">
      <p>Si ese correo tiene una cuenta, recibirás un enlace para elegir una contraseña nueva. Caduca en 1 hora.</p>
      <BackLink to="/login" label="Entrar" aria-label="Volver a entrar" />
    </section>

    <form v-else class="card" novalidate @submit.prevent="submit">
      <p class="muted">Solo para adultos. Si eres menor, pídele a tu adulto tutor que restablezca tu contraseña.</p>
      <label for="email">Correo</label>
      <input id="email" v-model="email" data-testid="email" type="email" autocomplete="email" />
      <p v-if="error" class="error" role="alert" data-testid="error">{{ error }}</p>
      <button class="primary" type="submit" data-testid="submit" :disabled="loading">Enviar enlace</button>
      <div class="links"><BackLink to="/login" label="Entrar" aria-label="Volver a entrar" /></div>
    </form>
  </div>
</template>
