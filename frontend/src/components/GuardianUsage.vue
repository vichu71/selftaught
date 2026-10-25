<script setup lang="ts">
import type { GuardianUsage } from '@/api/client'

defineProps<{ usage: GuardianUsage }>()

const tasks: Record<string, string> = {
  OUTLINE: 'Temarios',
  LESSON: 'Lecciones',
  ASSESSMENT: 'Ejercicios y exámenes',
  TUTOR: 'Tutor',
  INTAKE: 'Asistente de cursos',
  EMBEDDING: 'Búsqueda en su material',
}

const usd = (n: number) => `${n.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} USD`
</script>

<template>
  <section class="card" data-testid="guardian-usage">
    <h2>IA este mes</h2>
    <p>{{ usd(usage.spendUsd) }} de {{ usd(usage.limitUsd) }}</p>
    <p v-if="usage.reached" class="error" data-testid="guardian-usage-reached">Ha llegado a su tope del mes: no podrá usar la IA hasta el mes que viene.</p>
    <ul v-if="usage.byTask.length > 0" class="guardian-list">
      <li v-for="t in usage.byTask" :key="t.task">{{ tasks[t.task] ?? 'Otros' }}: {{ usd(t.spendUsd) }} <span class="muted">({{ t.calls }} llamadas)</span></li>
    </ul>
  </section>
</template>
