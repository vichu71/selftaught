<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api } from '@/api/client'
import { BLOCKED_TEXT } from '@/stores/course'

const router = useRouter()
const code = ref('')
const sending = ref(false)
const error = ref('')

async function send() {
  if (sending.value) return
  error.value = ''
  const value = code.value.trim()
  if (value === '') {
    error.value = 'Escribe el código que te han dado.'
    return
  }
  sending.value = true
  try {
    const copy = await api.redeemCode(value)
    await router.push(`/cursos/${copy.id}`)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) error.value = 'Ese código no vale. Revísalo y vuelve a probar.'
    else if (e instanceof ApiError && e.status === 429) error.value = 'Has probado muchos códigos seguidos. Espera un rato e inténtalo de nuevo.'
    else if (e instanceof ApiError && e.status === 422) error.value = BLOCKED_TEXT
    else error.value = 'No se pudo canjear el código. Inténtalo de nuevo.'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <main class="page" data-testid="redeem-view">
    <header class="topbar">
      <h1>Tengo un código</h1>
      <RouterLink to="/" data-testid="back-home">Volver al inicio</RouterLink>
    </header>

    <section class="card">
      <p data-testid="redeem-info">
        Escribe el código que te han dado y recibirás tu propia copia del curso, que podrás editar sin que cambie el original. La copia no incluye el material ni los datos de quien lo compartió.
      </p>
      <label for="redeem-input">Código</label>
      <input id="redeem-input" v-model="code" type="text" maxlength="40" autocomplete="off" autocapitalize="characters" placeholder="XXXX-XXXX-XXXX-XXXX" data-testid="redeem-input" @keydown.enter="send" />
      <p v-if="error" class="error" role="alert" data-testid="redeem-error">{{ error }}</p>
      <p class="actions">
        <button type="button" class="primary" data-testid="redeem-send" :disabled="sending" @click="send">Obtener mi copia</button>
      </p>
    </section>
  </main>
</template>
