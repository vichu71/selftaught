<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'

const route = useRoute()
const state = ref<'loading' | 'verified' | 'error'>('loading')

const resendEmail = ref('')
const resent = ref(false)
const resendError = ref('')

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    state.value = 'error'
    return
  }
  try {
    await api.verifyEmail(token)
    state.value = 'verified'
  } catch {
    state.value = 'error'
  }
})

async function resend() {
  const email = resendEmail.value.trim()
  resendError.value = ''
  if (!email) {
    resendError.value = 'Escribe tu correo.'
    return
  }
  try {
    await api.resendVerification(email)
    resent.value = true
  } catch {
    resendError.value = 'No se pudo reenviar. Inténtalo más tarde.'
  }
}
</script>

<template>
  <main class="page">
    <h1>Confirmar correo</h1>

    <section v-if="state === 'loading'" class="card"><p>Comprobando el enlace…</p></section>

    <section v-else-if="state === 'verified'" class="card" data-testid="verified">
      <p class="success">¡Correo confirmado! Tu cuenta ya está activa.</p>
      <RouterLink to="/login">Entrar</RouterLink>
    </section>

    <section v-else class="card">
      <p class="error" role="alert" data-testid="error">El enlace no es válido o ha caducado.</p>
      <p v-if="resent" class="success" data-testid="resent">Si la cuenta está pendiente, te hemos enviado un enlace nuevo.</p>
      <template v-else>
        <label for="resend-email">Pide uno nuevo con tu correo</label>
        <input id="resend-email" v-model="resendEmail" data-testid="resend-email" type="email" autocomplete="email" />
        <p v-if="resendError" class="field-error">{{ resendError }}</p>
        <button class="primary" type="button" data-testid="resend-submit" @click="resend">Reenviar enlace</button>
      </template>
    </section>
  </main>
</template>
