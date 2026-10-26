<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import MaterialList from '@/components/MaterialList.vue'
import { useAuthStore } from '@/stores/auth'
import { useMaterialStore } from '@/stores/material'

const auth = useAuthStore()
const material = useMaterialStore()
const input = ref<HTMLInputElement | null>(null)

onMounted(() => material.startPolling())
onBeforeUnmount(() => material.stopPolling())

async function onChoose(event: Event) {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  if (!file) return
  await material.upload(file)
  element.value = '' // permite volver a elegir el mismo fichero
}
</script>

<template>
  <div class="page wide" data-testid="material-view">
    <header class="topbar">
      <h1>Mi material</h1>
      <RouterLink to="/" data-testid="back-home">Volver al inicio</RouterLink>
    </header>

    <p v-if="auth.user?.kind === 'MINOR'" class="notice" data-testid="material-minor-notice">
      Tu adulto tutor puede ver los documentos que subes.
    </p>

    <section class="card">
      <h2>Subir un documento</h2>
      <p class="muted">PDF, Word (.docx) o texto (.txt, .md). Máximo 10 MB. Solo tú lo ves{{ auth.user?.kind === 'MINOR' ? ' y tu tutor' : '' }}.</p>
      <label for="material-file">Elegir fichero</label>
      <input
        id="material-file" ref="input" type="file" accept=".pdf,.docx,.txt,.md" data-testid="file-input"
        :disabled="material.uploading" @change="onChoose"
      />
      <p v-if="material.uploading" class="muted" role="status" data-testid="uploading">Subiendo…</p>
      <p v-if="material.uploadError" class="error" role="alert" data-testid="upload-error">{{ material.uploadError }}</p>
    </section>

    <section class="card">
      <h2>Mis documentos ({{ material.own.length }})</h2>
      <p v-if="material.listError" class="error" role="alert" data-testid="list-error">No se pudo cargar la lista.</p>
      <p v-else-if="material.own.length === 0" class="muted" data-testid="no-documents">Todavía no has subido ningún documento.</p>
      <p v-if="material.deleteError" class="error" role="alert" data-testid="delete-error">No se pudo borrar el documento.</p>
      <MaterialList :documents="material.own" @remove="material.remove" />
    </section>

    <section v-if="material.minors.length > 0" class="card" data-testid="minors-material">
      <h2>Material de mis menores</h2>
      <p class="muted">Solo lectura.</p>
      <div v-for="m in material.minors" :key="m.student.id" :data-testid="`minor-material-${m.student.id}`">
        <h3>{{ m.student.displayName }}</h3>
        <p v-if="m.documents.length === 0" class="muted">Sin documentos.</p>
        <MaterialList :documents="m.documents" readonly />
      </div>
    </section>
  </div>
</template>
