<script setup lang="ts">
import type { TutorMessage } from '@/api/client'
import MarkdownContent from '@/components/MarkdownContent.vue'

defineProps<{ messages: TutorMessage[] }>()

const marks: Record<string, string> = { BLOCKED: 'Bloqueado por el filtro de menores', FILTERED: 'Respuesta filtrada por el filtro de menores' }

const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <ol class="tutor-thread">
    <li v-for="m in messages" :key="m.id" class="tutor-msg" :class="m.role === 'USER' ? 'from-user' : 'from-tutor'" :data-testid="`guardian-msg-${m.id}`" :data-status="m.status">
      <p class="muted tutor-who">{{ m.role === 'USER' ? 'Alumno' : 'Tutor IA' }} · {{ when(m.createdAt) }}<template v-if="marks[m.status]"> · <strong>{{ marks[m.status] }}</strong></template></p>
      <MarkdownContent v-if="m.role === 'TUTOR'" :source="m.content" />
      <p v-else class="statement">{{ m.content }}</p>
      <p v-if="(m.sources ?? []).length > 0" class="muted">
        <template v-for="(s, i) in m.sources" :key="s.n"><template v-if="i > 0">, </template>Fuente {{ s.n }}<template v-if="s.page"> (página {{ s.page }})</template></template>
      </p>
    </li>
  </ol>
</template>
