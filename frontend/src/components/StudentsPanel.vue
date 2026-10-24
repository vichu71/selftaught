<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError, api, type Student } from '@/api/client'

const students = ref<Student[]>([])
const listError = ref(false)

const form = reactive({ displayName: '', loginHint: '', password: '' })
const creating = ref(false)
const createError = ref('')
const createdLoginId = ref('')

const resettingId = ref('')
const resetPassword = ref('')
const resetError = ref('')
const resetDone = ref(false)

async function load() {
  try {
    students.value = await api.listStudents()
    listError.value = false
  } catch {
    listError.value = true
  }
}

onMounted(load)

async function create() {
  if (creating.value) return
  const displayName = form.displayName.trim()
  const loginHint = form.loginHint.trim() || displayName
  createdLoginId.value = ''
  if (!displayName || displayName.length > 50) {
    createError.value = 'Escribe un nombre (máximo 50 caracteres).'
    return
  }
  if (form.password.length < 10 || form.password.length > 128) {
    createError.value = 'La contraseña debe tener entre 10 y 128 caracteres.'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = await api.createStudent({ displayName, loginHint, password: form.password })
    createdLoginId.value = created.loginId
    form.displayName = ''
    form.loginHint = ''
    form.password = ''
    await load()
  } catch (e) {
    createError.value =
      e instanceof ApiError && e.status === 409
        ? 'Ya tienes un menor con ese nombre.'
        : 'No se pudo crear. Revisa los datos e inténtalo de nuevo.'
  } finally {
    creating.value = false
  }
}

function openReset(id: string) {
  resettingId.value = id
  resetPassword.value = ''
  resetError.value = ''
  resetDone.value = false
}

async function confirmReset() {
  if (resetPassword.value.length < 10 || resetPassword.value.length > 128) {
    resetError.value = 'La contraseña debe tener entre 10 y 128 caracteres.'
    return
  }
  try {
    await api.resetStudentPassword(resettingId.value, resetPassword.value)
    resettingId.value = ''
    resetDone.value = true
  } catch {
    resetError.value = 'No se pudo cambiar la contraseña.'
  }
}

async function setActive(id: string, active: boolean) {
  try {
    await (active ? api.reactivateStudent(id) : api.deactivateStudent(id))
  } finally {
    await load()
  }
}
</script>

<template>
  <section class="card" data-testid="students-panel">
    <h2>Mis menores</h2>
    <p class="muted">Los menores entran con el usuario que se les genera y la contraseña que tú elijas. Tú ves toda su actividad.</p>

    <p v-if="listError" class="error" role="alert" data-testid="list-error">No se pudo cargar la lista.</p>
    <p v-else-if="students.length === 0" class="muted" data-testid="no-students">Todavía no has dado de alta a nadie.</p>

    <ul class="students">
      <li v-for="s in students" :key="s.id" class="student" :data-testid="`student-${s.id}`">
        <div>
          <strong>{{ s.displayName }}</strong>
          <span class="badge"> · usuario: {{ s.loginId }} · {{ s.status === 'ACTIVE' ? 'Activo' : 'De baja' }}</span>
        </div>
        <div class="actions">
          <button type="button" :data-testid="`reset-${s.id}`" @click="openReset(s.id)">Cambiar contraseña</button>
          <button v-if="s.status === 'ACTIVE'" type="button" :data-testid="`deactivate-${s.id}`" @click="setActive(s.id, false)">Dar de baja</button>
          <button v-else type="button" :data-testid="`reactivate-${s.id}`" @click="setActive(s.id, true)">Reactivar</button>
        </div>
      </li>
    </ul>

    <div v-if="resettingId" class="notice" role="dialog" aria-label="Cambiar contraseña">
      <label for="new-password">Contraseña nueva (mínimo 10 caracteres)</label>
      <input id="new-password" v-model="resetPassword" data-testid="new-password" type="password" autocomplete="new-password" />
      <p v-if="resetError" class="field-error">{{ resetError }}</p>
      <button type="button" class="primary" data-testid="reset-confirm" @click="confirmReset">Guardar contraseña</button>
    </div>
    <p v-if="resetDone" class="success" data-testid="reset-done">Contraseña cambiada.</p>

    <h2 style="margin-top: 1.5rem">Dar de alta a un menor</h2>
    <form data-testid="create-form" novalidate @submit.prevent="create">
      <label for="student-name">Nombre</label>
      <input id="student-name" v-model="form.displayName" data-testid="display-name" autocomplete="off" />

      <label for="student-hint">Usuario deseado (opcional)</label>
      <input id="student-hint" v-model="form.loginHint" data-testid="login-hint" autocomplete="off" autocapitalize="none" />

      <label for="student-password">Contraseña (mínimo 10 caracteres)</label>
      <input id="student-password" v-model="form.password" data-testid="password" type="password" autocomplete="new-password" />

      <p v-if="createError" class="error" role="alert" data-testid="create-error">{{ createError }}</p>
      <p v-if="createdLoginId" class="success" data-testid="created-login-id">
        Creado. Su usuario para entrar es: <strong>{{ createdLoginId }}</strong>
      </p>

      <button class="primary" type="submit" data-testid="create-submit" :disabled="creating">Dar de alta</button>
    </form>
  </section>
</template>
