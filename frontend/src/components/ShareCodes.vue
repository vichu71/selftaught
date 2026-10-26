<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, api, type ShareCodeInfo } from '@/api/client'

const props = defineProps<{ courseId: string; status: string }>()

const errorMessages: Record<string, string> = {
  SHARE_LIMIT: 'Este curso ya tiene cinco códigos activos. Revoca alguno antes de crear otro.',
  COURSE_NOT_READY: 'Solo se puede compartir un curso terminado.',
}

const codes = ref<ShareCodeInfo[]>([])
const days = ref(7)
const created = ref('')
const error = ref('')
const busy = ref(false)
const revoking = ref('')

const when = (iso: string) => new Date(iso).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })

async function load() {
  try {
    codes.value = (await api.listShareCodes(props.courseId)) ?? []
  } catch {
    error.value = 'No se pudo cargar la lista de códigos.'
  }
}

onMounted(load)

async function create() {
  if (busy.value) return
  error.value = ''
  const n = Number(days.value)
  if (!Number.isInteger(n) || n < 1 || n > 90) {
    error.value = 'La caducidad debe ser de 1 a 90 días.'
    return
  }
  busy.value = true
  try {
    created.value = (await api.createShareCode(props.courseId, n)).code
    await load()
  } catch (e) {
    error.value = (e instanceof ApiError && e.code && errorMessages[e.code]) || 'No se pudo crear el código. Inténtalo de nuevo.'
  } finally {
    busy.value = false
  }
}

async function copy() {
  try {
    await navigator.clipboard.writeText(created.value)
  } catch {
    // sin portapapeles se puede copiar a mano: el código está a la vista
  }
}

async function revoke(id: string) {
  error.value = ''
  try {
    await api.revokeShareCode(props.courseId, id)
    revoking.value = ''
    await load()
  } catch {
    error.value = 'No se pudo revocar el código. Inténtalo de nuevo.'
  }
}

function state(code: ShareCodeInfo): string {
  if (code.revokedAt) return 'Revocado'
  return code.active ? 'Activo' : 'Caducado'
}
</script>

<template>
  <section class="card" data-testid="share-section">
    <h2>Compartir</h2>
    <p class="muted">Crea un código y pásalo a quien quieras: recibirá su propia copia del curso, sin tu material ni tus datos.</p>

    <p v-if="status !== 'READY'" class="muted" data-testid="share-not-ready">Solo se puede compartir un curso terminado.</p>
    <p class="actions">
      <label for="share-days">Caduca en</label>
      <input id="share-days" v-model.number="days" type="number" min="1" max="90" data-testid="share-days" :disabled="status !== 'READY'" />
      <span>días</span>
      <button type="button" class="primary" data-testid="share-create" :disabled="busy || status !== 'READY'" @click="create">Crear código</button>
    </p>

    <div v-if="created" class="notice" data-testid="share-new">
      <p>Tu código: <strong data-testid="share-new-code">{{ created }}</strong></p>
      <p class="muted" data-testid="share-once">Se muestra una sola vez: cópialo ahora.</p>
      <p class="actions">
        <button type="button" data-testid="share-copy" @click="copy">Copiar</button>
        <button type="button" data-testid="share-hide-code" @click="created = ''">Ya lo he copiado</button>
      </p>
    </div>

    <p v-if="error" class="error" role="alert" data-testid="share-error">{{ error }}</p>

    <p v-if="codes.length === 0" class="muted" data-testid="share-empty">Todavía no has creado ningún código.</p>
    <ul v-else class="guardian-list">
      <li v-for="c in codes" :key="c.id" :data-testid="`share-code-${c.id}`">
        {{ state(c) }} · creado el {{ when(c.createdAt) }} · caduca el {{ when(c.expiresAt) }} · {{ c.uses }} {{ c.uses === 1 ? 'uso' : 'usos' }}
        <template v-if="c.active">
          <button v-if="revoking !== c.id" type="button" :data-testid="`share-revoke-${c.id}`" @click="revoking = c.id">Revocar</button>
          <span v-else>
            ¿Revocar este código?
            <button type="button" class="primary" data-testid="share-revoke-confirm" @click="revoke(c.id)">Sí, revocar</button>
            <button type="button" @click="revoking = ''">No</button>
          </span>
        </template>
      </li>
    </ul>
  </section>
</template>
