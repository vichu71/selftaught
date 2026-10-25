<script setup lang="ts">
import { computed } from 'vue'
import type { OutlineTopic, PieceRef, PieceStatus, StudyOverview, TopicStudy } from '@/api/client'

const props = defineProps<{ courseId: string; outline: OutlineTopic[]; study?: StudyOverview | null }>()

const labels: Record<PieceStatus, string> = {
  DONE: 'Lista',
  PENDING: 'En cola',
  FAILED: 'Hubo un error',
}

const stateClass = (piece: PieceRef) => `status-${piece.status === 'DONE' ? 'ready' : piece.status === 'FAILED' ? 'error' : 'uploaded'}`

const byTopic = computed(() => new Map<string, TopicStudy>((props.study?.topics ?? []).map((t) => [t.id, t])))
const doneLessons = computed(() => new Set((props.study?.topics ?? []).flatMap((t) => t.lessons.filter((l) => l.done).map((l) => l.pieceId))))

function examNote(topicId: string): string {
  const exam = byTopic.value.get(topicId)?.exam
  if (!exam) return ''
  if (exam.attempts === 0) return 'Sin hacer'
  return exam.passed ? `Mejor nota ${exam.best} % · tema superado` : `Mejor nota ${exam.best} % · repasa y vuelve a intentarlo`
}
</script>

<template>
  <ol class="outline" data-testid="outline">
    <li v-for="topic in outline" :key="topic.id" class="outline-topic">
      <div class="topic-line">
        <h3>{{ topic.title }}</h3>
        <span v-if="byTopic.get(topic.id)" class="muted" :data-testid="`topic-percent-${topic.id}`">{{ byTopic.get(topic.id)?.percent }} %</span>
      </div>
      <p class="muted">{{ topic.summary }}</p>
      <ul class="outline-pieces">
        <template v-for="chapter in topic.chapters" :key="chapter.id">
          <li v-if="chapter.lesson" :data-testid="`piece-${chapter.lesson.id}`">
            <RouterLink v-if="chapter.lesson.status === 'DONE'" :to="`/cursos/${courseId}/piezas/${chapter.lesson.id}`" :data-testid="`piece-link-${chapter.lesson.id}`">
              Lección: {{ chapter.title }}
            </RouterLink>
            <span v-else>Lección: {{ chapter.title }}</span>
            <span v-if="doneLessons.has(chapter.lesson.id)" class="status status-ready" :data-testid="`done-${chapter.lesson.id}`">✓ Hecha</span>
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
          <span v-if="examNote(topic.id)" class="muted" :data-testid="`exam-best-${topic.id}`">{{ examNote(topic.id) }}</span>
          <span class="status" :class="stateClass(topic.exam)">{{ labels[topic.exam.status] }}</span>
        </li>
      </ul>
    </li>
  </ol>
</template>
