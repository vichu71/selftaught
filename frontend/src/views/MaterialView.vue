<script setup lang="ts">
import BackLink from '@/ui/BackLink.vue'
import { onBeforeUnmount, onMounted } from 'vue'
import MaterialList from '@/components/MaterialList.vue'
import DropZone from '@/material/DropZone.vue'
import EmptyNote from '@/ui/EmptyNote.vue'
import { useAuthStore } from '@/stores/auth'
import { useMaterialStore } from '@/stores/material'

const auth = useAuthStore()
const material = useMaterialStore()

onMounted(() => material.startPolling())
onBeforeUnmount(() => material.stopPolling())

function onChoose(file: File) {
  return material.upload(file)
}
</script>

<template>
  <div class="page wide" data-testid="material-view">
    <header class="topbar">
      <h1>Mi material</h1>
      <BackLink data-testid="back-home" />
    </header>

    <p v-if="auth.user?.kind === 'MINOR'" class="notice" data-testid="material-minor-notice">
      Tu adulto tutor puede ver los documentos que subes.
    </p>

    <section class="card">
      <h2>Subir un documento</h2>
      <DropZone :uploading="material.uploading" :minor="auth.user?.kind === 'MINOR'" @choose="onChoose" />
      <p v-if="material.uploadError" class="error" role="alert" data-testid="upload-error">{{ material.uploadError }}</p>
    </section>

    <section class="card">
      <h2>Mis documentos ({{ material.own.length }})</h2>
      <p v-if="material.listError" class="error" role="alert" data-testid="list-error">No se pudo cargar la lista.</p>
      <EmptyNote v-else-if="material.own.length === 0" message="Todavía no has subido ningún documento." data-testid="no-documents" />
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
