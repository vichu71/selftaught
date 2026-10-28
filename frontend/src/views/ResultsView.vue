<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type CourseResult, type Results, type Usage, type UsageTasks } from '@/api/client'
import CourseCard from '@/results/CourseCard.vue'
import TopeMeter from '@/results/TopeMeter.vue'
import { taskLabel } from '@/results/taskLabels'
import BackLink from '@/ui/BackLink.vue'
import BarList from '@/ui/BarList.vue'
import { num, times, usd } from '@/ui/format'
import StatCard from '@/ui/StatCard.vue'
import UiState from '@/ui/UiState.vue'

// Las tres lecturas van a la vez y cada una falla por separado: si falta una, su bloque lo dice y el resto de la pantalla se sigue viendo.
const results = ref<Results | null>(null)
const usage = ref<Usage | null>(null)
const tasks = ref<UsageTasks | null>(null)
const loading = ref(true)
const resultsFailed = ref(false)
const usageFailed = ref(false)
const tasksFailed = ref(false)

const money = new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const courses = computed<CourseResult[]>(() => results.value?.courses ?? [])
const showMoney = computed(() => results.value?.totalCostUsd !== undefined)
const ready = computed(() => courses.value.filter((c) => c.status === 'READY').length)
/** Un menor no recibe importes ni topes: se sabe porque el servidor no le manda el gasto. */
const withMoney = computed(() => usage.value?.you.spendUsd !== undefined)
const taskItems = computed(() => (tasks.value?.tasks ?? []).map((t) => ({ label: taskLabel(t.task), value: withMoney.value ? (t.spendUsd ?? 0) : t.calls })))

onMounted(async () => {
  const [r, u, t] = await Promise.allSettled([api.getResults(), api.getUsage(), api.getUsageTasks()])
  if (r.status === 'fulfilled') results.value = r.value
  else resultsFailed.value = true
  if (u.status === 'fulfilled') usage.value = u.value
  else usageFailed.value = true
  if (t.status === 'fulfilled') tasks.value = t.value
  else tasksFailed.value = true
  loading.value = false
})
</script>

<template>
  <div class="page wide" data-testid="results-view">
    <header class="topbar">
      <h1>Resultados</h1>
      <BackLink data-testid="back-home" />
    </header>

    <UiState v-if="loading" state="loading" message="Cargando tus resultados…" data-testid="results-loading" />

    <template v-else>
      <UiState v-if="usageFailed" state="error" message="No se pudo cargar tu gasto de este mes." data-testid="results-usage-error" />
      <section v-else-if="usage" class="card" data-testid="month">
        <h2>Este mes ({{ usage.month }})</h2>
        <p v-if="usage.you.calls === 0" class="muted">La IA todavía no te ha ayudado este mes.</p>
        <div class="results-stats">
          <StatCard label="La IA te ha ayudado" :value="times(usage.you.calls)" hint="Este mes" data-testid="results-stat-calls" />
          <StatCard v-if="usage.you.spendUsd !== undefined" label="Gasto del mes" :value="`${usd(usage.you.spendUsd)} USD`" data-testid="results-stat-spend" />
          <StatCard v-if="results" label="Cursos listos" :value="num(ready)" :hint="`de ${num(courses.length)}`" data-testid="results-stat-ready" />
        </div>
        <TopeMeter v-if="usage.you.spendUsd !== undefined && usage.you.limitUsd !== undefined" label="Tu tope de este mes" :spend-usd="usage.you.spendUsd" :limit-usd="usage.you.limitUsd" :reached="usage.you.limitReached" />
        <p v-if="usage.you.limitReached" class="error" role="alert" data-testid="limit-reached">
          {{ withMoney ? 'Has llegado a tu tope de este mes: los cursos nuevos se pausan hasta que se renueve.' : 'Has llegado al máximo de este mes: los cursos nuevos se pausan hasta que se renueve.' }}
        </p>
      </section>

      <UiState v-if="tasksFailed" state="error" message="No se pudo cargar en qué te ha ayudado la IA." data-testid="results-tasks-error" />
      <div v-else-if="taskItems.length > 0" data-testid="results-tasks">
        <BarList :items="taskItems" title="En qué te ha ayudado la IA" :unit="withMoney ? 'USD' : 'veces'" />
      </div>

      <section v-if="usage && usage.minors && usage.minors.length > 0" class="card" data-testid="results-minors">
        <h2>Tus peques</h2>
        <ul class="results-minors">
          <li v-for="m in usage.minors" :key="m.id" class="results-minor">
            <p><strong>{{ m.displayName }}</strong> · la IA le ha ayudado {{ times(m.calls) }}</p>
            <TopeMeter label="Tope de este mes" :spend-usd="m.spendUsd" :limit-usd="m.limitUsd" :reached="m.limitReached" />
          </li>
        </ul>
      </section>

      <UiState v-if="resultsFailed" state="error" message="No se pudieron cargar los resultados. Inténtalo más tarde." data-testid="results-error" />
      <section v-else-if="results" class="card">
        <h2>Tus cursos</h2>
        <p v-if="courses.length === 0" class="muted" data-testid="results-empty">Todavía no tienes cursos. Cuando crees uno, sus resultados saldrán aquí.</p>
        <ul v-else class="result-grid">
          <CourseCard v-for="c in courses" :key="c.id" :course="c" :show-money="showMoney" />
        </ul>
        <p v-if="showMoney && courses.length > 0" data-testid="results-total"><strong>Total de los cursos:</strong> {{ money.format(results.totalCostUsd ?? 0) }} USD</p>
      </section>
    </template>
  </div>
</template>
