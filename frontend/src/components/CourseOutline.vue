<script setup lang="ts">
import type { OutlineTopic, PieceRef, PieceStatus } from '@/api/client'

defineProps<{ courseId: string; outline: OutlineTopic[] }>()

const labels: Record<PieceStatus, string> = {
  DONE: 'Lista',
  PENDING: 'En cola',
  FAILED: 'Hubo un error',
}

const stateClass = (piece: PieceRef) => `status-${piece.status === 'DONE' ? 'ready' : piece.status === 'FAILED' ? 'error' : 'uploaded'}`
</script>

<template>
  <ol class="outline" data-testid="outline">
    <li v-for="topic in outline" :key="topic.id" class="outline-topic">
      <h3>{{ topic.title }}</h3>
      <p class="muted">{{ topic.summary }}</p>
      <ul class="outline-pieces">
        <template v-for="chapter in topic.chapters" :key="chapter.id">
          <li v-if="chapter.lesson" :data-testid="`piece-${chapter.lesson.id}`">
            <RouterLink v-if="chapter.lesson.status === 'DONE'" :to="`/cursos/${courseId}/piezas/${chapter.lesson.id}`" :data-testid="`piece-link-${chapter.lesson.id}`">
              Lección: {{ chapter.title }}
            </RouterLink>
            <span v-else>Lección: {{ chapter.title }}</span>
            <span class="status" :class="stateClass(chapter.lesson)">{{ labels[chapter.lesson.status] }}</span>
          </li>
          <li v-if="chapter.exercises" :data-testid="`piece-${chapter.exercises.id}`">
            <RouterLink v-if="chapter.exercises.status === 'DONE'" :to="`/cursos/${courseId}/piezas/${chapter.exercises.id}`" :data-testid="`piece-link-${chapter.exercises.id}`">
              Ejercicios: {{ chapter.title }}
            </RouterLink>
            <span v-else>Ejercicios: {{ chapter.title }}</span>
            <span class="status" :class="stateClass(chapter.exercises)">{{ labels[chapter.exercises.status] }}</span>
          </li>
        </template>
        <li v-if="topic.exam" :data-testid="`piece-${topic.exam.id}`">
          <RouterLink v-if="topic.exam.status === 'DONE'" :to="`/cursos/${courseId}/piezas/${topic.exam.id}`" :data-testid="`piece-link-${topic.exam.id}`">
            Examen del tema
          </RouterLink>
          <span v-else>Examen del tema</span>
          <span class="status" :class="stateClass(topic.exam)">{{ labels[topic.exam.status] }}</span>
        </li>
      </ul>
    </li>
  </ol>
</template>
