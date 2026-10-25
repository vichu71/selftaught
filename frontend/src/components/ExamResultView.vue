<script setup lang="ts">
import type { ExamResult } from '@/api/client'

defineProps<{ result: ExamResult }>()
</script>

<template>
  <section class="exam-result">
    <p class="status-line" data-testid="exam-score">{{ result.correct }} de {{ result.total }} · {{ result.percent }} %</p>
    <p data-testid="exam-passed">
      {{ result.passed ? 'Tema superado. ¡Buen trabajo!' : 'Todavía no: repasa las lecciones y vuelve a intentarlo.' }}
    </p>

    <ol class="exam">
      <li v-for="(q, i) in result.questions" :key="i" class="exercise" :data-testid="`result-q-${i}`">
        <p class="statement">{{ q.text }}</p>
        <p>
          <strong>{{ q.chosen === null || q.chosen === undefined ? 'Sin contestar' : q.ok ? 'Correcta' : 'Incorrecta' }}</strong>
        </p>
        <p v-if="q.chosen !== null && q.chosen !== undefined">Tu respuesta: {{ q.options[q.chosen] }}</p>
        <p v-if="!q.ok">La correcta: {{ q.options[q.correct] }}</p>
        <p v-if="q.explanation" class="statement muted">{{ q.explanation }}</p>
      </li>
    </ol>
  </section>
</template>
