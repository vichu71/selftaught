<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type CourseResult, type CourseStatus, type Results, type Usage } from '@/api/client'

const results = ref<Results | null>(null)
const usage = ref<Usage | null>(null)
const failed = ref(false)

const labels: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola',
  GENERATING: 'Preparándose',
  PAUSED: 'En pausa',
  READY: 'Listo',
  FAILED: 'No se pudo preparar',
}

const money = new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const usd = (value: number) => `${money.format(value)} USD`

const courses = computed<CourseResult[]>(() => results.value?.courses ?? [])
const showMoney = computed(() => results.value?.totalCostUsd !== undefined)

onMounted(async () => {
  try {
    ;[results.value, usage.value] = await Promise.all([api.getResults(), api.getUsage()])
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="page wide" data-testid="results-view">
    <header class="topbar">
      <h1>Resultados</h1>
      <RouterLink to="/" data-testid="back-home">Volver al inicio</RouterLink>
    </header>

    <p v-if="failed" class="error" role="alert" data-testid="results-error">No se pudieron cargar los resultados. Inténtalo más tarde.</p>

    <template v-else-if="results">
      <section v-if="usage" class="card" data-testid="month">
        <h2>Este mes ({{ usage.month }})</h2>
        <p v-if="usage.you.spendUsd !== undefined && usage.you.limitUsd !== undefined">
          Llevas gastados {{ usd(usage.you.spendUsd) }} de tu tope de {{ usd(usage.you.limitUsd) }}. {{ usage.you.calls }} llamadas a la IA.
        </p>
        <p v-else>{{ usage.you.calls }} llamadas a la IA este mes.</p>
        <p v-if="usage.you.limitReached" class="error" role="alert" data-testid="limit-reached">
          Has llegado a tu tope de este mes: los cursos nuevos se pausan hasta que se renueve.
        </p>
      </section>

      <section class="card">
        <h2>Tus cursos</h2>
        <p v-if="courses.length === 0" class="muted" data-testid="results-empty">Todavía no tienes cursos. Cuando crees uno, sus resultados saldrán aquí.</p>
        <ul class="documents">
          <li v-for="c in courses" :key="c.id" class="document result" :data-testid="`result-${c.id}`">
            <div class="document-main">
              <RouterLink :to="`/cursos/${c.id}`" :data-testid="`result-link-${c.id}`">{{ c.title }}</RouterLink>
              <span class="status">{{ labels[c.status] }}</span>
            </div>
            <p class="muted result-numbers">
              {{ c.done }} de {{ c.total }} piezas hechas<template v-if="c.failed > 0">
                · <span class="result-failed" :data-testid="`result-failed-${c.id}`">{{ c.failed }} con error</span></template><template v-if="c.pending > 0"> · {{ c.pending }} pendientes</template>
              · {{ c.calls }} llamadas<template v-if="c.costUsd !== undefined"> · <span :data-testid="`result-cost-${c.id}`">{{ usd(c.costUsd) }}</span></template>
            </p>
          </li>
        </ul>
        <p v-if="showMoney && courses.length > 0" data-testid="results-total"><strong>Total de los cursos:</strong> {{ usd(results.totalCostUsd ?? 0) }}</p>
      </section>
    </template>
  </div>
</template>
