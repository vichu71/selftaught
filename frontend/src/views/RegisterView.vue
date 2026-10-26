<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api } from '@/api/client'

const form = reactive({ displayName: '', email: '', password: '' })
const errors = reactive({ displayName: '', email: '', password: '' })
const loading = ref(false)
const error = ref('')
const sentTo = ref('')

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// La validación del navegador es solo comodidad: la que cuenta es la del backend
function validate(): boolean {
  errors.displayName = form.displayName.trim() && form.displayName.trim().length <= 50 ? '' : 'Escribe tu nombre (máximo 50 caracteres).'
  errors.email = EMAIL.test(form.email.trim()) ? '' : 'Escribe un correo válido.'
  errors.password = form.password.length >= 10 && form.password.length <= 128 ? '' : 'La contraseña debe tener entre 10 y 128 caracteres.'
  return !errors.displayName && !errors.email && !errors.password
}

async function submit() {
  if (loading.value || !validate()) return
  loading.value = true
  error.value = ''
  const email = form.email.trim()
  try {
    await api.register({ email, password: form.password, displayName: form.displayName.trim() })
    sentTo.value = email
  } catch {
    error.value = 'No se pudo crear la cuenta. Revisa los datos e inténtalo de nuevo.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1>Crear cuenta de adulto</h1>

    <section v-if="sentTo" class="card" data-testid="check-email">
      <h2>Revisa tu correo</h2>
      <p>Te hemos enviado un enlace a <strong>{{ sentTo }}</strong> para activar tu cuenta. Caduca en 24 horas.</p>
      <RouterLink to="/login">Volver a entrar</RouterLink>
    </section>

    <form v-else class="card" novalidate @submit.prevent="submit">
      <p class="muted">Los menores no se registran solos: tú los das de alta después desde tu cuenta.</p>

      <label for="display-name">Tu nombre</label>
      <input id="display-name" v-model="form.displayName" data-testid="display-name" autocomplete="name" />
      <p v-if="errors.displayName" class="field-error" data-testid="display-name-error">{{ errors.displayName }}</p>

      <label for="email">Correo</label>
      <input id="email" v-model="form.email" data-testid="email" type="email" autocomplete="email" />
      <p v-if="errors.email" class="field-error" data-testid="email-error">{{ errors.email }}</p>

      <label for="password">Contraseña (mínimo 10 caracteres)</label>
      <input id="password" v-model="form.password" data-testid="password" type="password" autocomplete="new-password" />
      <p v-if="errors.password" class="field-error" data-testid="password-error">{{ errors.password }}</p>

      <p v-if="error" class="error" role="alert" data-testid="error">{{ error }}</p>

      <button class="primary" type="submit" data-testid="submit" :disabled="loading">
        {{ loading ? 'Creando…' : 'Crear cuenta' }}
      </button>
      <div class="links"><RouterLink to="/login">Ya tengo cuenta</RouterLink></div>
    </form>
  </div>
</template>
