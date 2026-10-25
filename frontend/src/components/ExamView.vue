<script setup lang="ts">
import type { ExamQuestion } from '@/api/client'

defineProps<{ questions: ExamQuestion[] }>()
</script>

<template>
  <ol class="exam" data-testid="exam">
    <li v-for="(question, i) in questions" :key="i" class="exercise" :data-testid="`question-${i}`">
      <p class="statement">{{ question.text }}</p>
      <ol class="options" type="A">
        <li v-for="(option, j) in question.options" :key="j">{{ option }}</li>
      </ol>
      <details>
        <summary>Ver respuesta</summary>
        <p class="statement"><strong>Correcta:</strong> {{ question.options[question.correct] }}</p>
        <p v-if="question.explanation" class="statement">{{ question.explanation }}</p>
      </details>
    </li>
  </ol>
</template>
