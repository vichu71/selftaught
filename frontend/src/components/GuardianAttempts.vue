<script setup lang="ts">
import type { ExerciseResult, GuardianAttempts } from '@/api/client'

defineProps<{ attempts: GuardianAttempts }>()

const results: Record<ExerciseResult, string> = { CORRECT: 'Acertado', PARTIAL: 'Casi', INCORRECT: 'Fallado' }

const when = (iso: string) => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <section class="card">
    <h2>Intentos</h2>
    <p v-if="attempts.pieces.length === 0" class="muted" data-testid="guardian-attempts-empty">Todavía no ha hecho ejercicios ni exámenes de este curso.</p>
    <div v-else data-testid="guardian-attempts">
      <div v-for="piece in attempts.pieces" :key="piece.pieceId" class="guardian-piece">
        <h3>{{ piece.kind === 'EXAM' ? 'Examen' : 'Ejercicios' }}: {{ piece.title }}</h3>

        <div v-for="exercise in piece.exercises ?? []" :key="exercise.item">
          <p class="statement"><strong>Ejercicio {{ exercise.item + 1 }}.</strong> {{ exercise.statement }}</p>
          <ul class="guardian-list">
            <li v-for="(a, i) in exercise.attempts" :key="i">
              <span class="muted">{{ when(a.createdAt) }}</span>
              <p class="statement">{{ a.answer }}</p>
              <p v-if="a.selfResult || a.aiResult" class="muted">
                <span v-if="a.selfResult">Él mismo: {{ results[a.selfResult] }}. </span>
                <span v-if="a.aiResult">La IA: {{ results[a.aiResult] }}. </span>
                <span v-if="a.aiComment" class="statement">{{ a.aiComment }}</span>
              </p>
            </li>
          </ul>
        </div>

        <ul v-if="piece.exams" class="guardian-list">
          <li v-for="(e, i) in piece.exams" :key="i">
            <span class="muted">{{ when(e.submittedAt) }}</span> · {{ e.correct }} de {{ e.total }} ({{ e.percent }} %) · {{ e.passed ? 'tema superado' : 'sin superar' }}
            <ul v-if="e.failed.length > 0">
              <li v-for="(q, j) in e.failed" :key="j" class="statement">Falló: {{ q }}</li>
            </ul>
          </li>
        </ul>
      </div>
      <p v-if="attempts.truncated" class="muted" data-testid="guardian-truncated">Se muestran solo los intentos más recientes de cada ejercicio y examen.</p>
    </div>
  </section>
</template>
