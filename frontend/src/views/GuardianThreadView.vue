<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type TutorMessage } from '@/api/client'
import ThreadView from '@/components/ThreadView.vue'

const route = useRoute()
const studentId = computed(() => String(route.params.id))

const messages = ref<TutorMessage[]>([])
const loading = ref(true)
const notFound = ref(false)
const failed = ref(false)

onMounted(async () => {
  try {
    const thread = await api.getGuardianThread(studentId.value, String(route.params.courseId), String(route.params.pieceId))
    messages.value = thread?.messages ?? []
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="page wide" data-testid="guardian-thread">
    <header class="topbar">
      <h1>Conversación con el tutor</h1>
      <RouterLink :to="`/alumnos/${studentId}`" data-testid="guardian-back">Volver al alumno</RouterLink>
    </header>

    <p v-if="loading" class="muted" data-testid="guardian-loading">Cargando…</p>
    <p v-else-if="notFound" class="error" role="alert" data-testid="guardian-not-found">No encontramos esta conversación entre las de tu alumno.</p>
    <p v-else-if="failed" class="error" role="alert" data-testid="guardian-error">No se ha podido cargar. Inténtalo más tarde.</p>
    <template v-else>
      <p class="muted">Solo lectura. Tu alumno sabe que puedes ver esta conversación. Texto generado por IA: el tutor puede equivocarse.</p>
      <p v-if="messages.length === 0" class="muted" data-testid="guardian-thread-empty">Todavía no hay mensajes en esta lección.</p>
      <ThreadView v-else :messages="messages" />
    </template>
  </main>
</template>
