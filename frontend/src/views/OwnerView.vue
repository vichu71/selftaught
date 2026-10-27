<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ApiError, api, type OwnerAccounts, type OwnerAi, type OwnerHealth, type OwnerPrices, type OwnerRange, type OwnerUsage,
} from '@/api/client'
import BarList from '@/owner/BarList.vue'
import DataTable from '@/owner/DataTable.vue'
import LineChart from '@/owner/LineChart.vue'
import StatCard from '@/owner/StatCard.vue'
import UserList from '@/owner/UserList.vue'
import { dateOf, megabytes, millis, num, percent, usd } from '@/owner/format'
import UiButton from '@/ui/UiButton.vue'
import UiState from '@/ui/UiState.vue'
import UiTabs from '@/ui/UiTabs.vue'


const tabs = [{ id: 'ai', label: 'Gasto de IA' }, { id: 'users', label: 'Usuarios' }, { id: 'usage', label: 'Uso' }, { id: 'health', label: 'Salud' }]
const ranges: OwnerRange[] = [7, 30, 90]

const tab = ref<string>('ai')
const range = ref<OwnerRange>(30)
const loading = ref(false)
const failed = ref('')

const ai = ref<OwnerAi | null>(null)
const prices = ref<OwnerPrices | null>(null)
const accounts = ref<OwnerAccounts | null>(null)
const usage = ref<OwnerUsage | null>(null)
const health = ref<OwnerHealth | null>(null)
const list = ref<InstanceType<typeof UserList> | null>(null) // la lista de usuarios se recarga con «Actualizar»

/** Pide solo lo que necesita la pestaña abierta; nada se refresca solo. */
async function load() {
  loading.value = true
  failed.value = ''
  try {
    if (tab.value === 'ai') {
      const [report, table] = await Promise.all([api.ownerAi(range.value), api.ownerPrices()])
      ai.value = report
      prices.value = table
    } else if (tab.value === 'users') {
      const [report] = await Promise.all([api.ownerAccounts(range.value), list.value?.reload()])
      accounts.value = report
    } else if (tab.value === 'usage') {
      usage.value = await api.ownerUsage(range.value)
    } else {
      health.value = await api.ownerHealth()
    }
  } catch (e) {
    failed.value = e instanceof ApiError && e.status === 429
      ? 'Demasiadas consultas. Espera un poco antes de volver a pedir datos.'
      : 'No se pudieron cargar los datos. Inténtalo de nuevo.'
  } finally {
    loading.value = false
  }
}

/** Solo el rango cambia las cifras de Gasto (sin tocar los precios), Usuarios y Uso. */
async function changeRange(next: OwnerRange) {
  if (next === range.value) return
  range.value = next
  failed.value = ''
  loading.value = true
  try {
    if (tab.value === 'ai') ai.value = await api.ownerAi(next)
    else if (tab.value === 'users') accounts.value = await api.ownerAccounts(next)
    else if (tab.value === 'usage') usage.value = await api.ownerUsage(next)
  } catch (e) {
    failed.value = e instanceof ApiError && e.status === 429 ? 'Demasiadas consultas. Espera un poco antes de volver a pedir datos.' : 'No se pudieron cargar los datos. Inténtalo de nuevo.'
  } finally {
    loading.value = false
  }
}

watch(tab, () => void load())
onMounted(load)

// ---------- estado de las cifras ----------

const globalTone = computed<'ok' | 'warn' | 'bad'>(() => {
  const p = ai.value?.globalLimit.percent ?? 0
  return p >= 100 ? 'bad' : p >= 80 ? 'warn' : 'ok'
})
const globalStatus = computed(() => ({ ok: 'Dentro del tope', warn: 'Cerca del tope', bad: 'Tope superado' })[globalTone.value])

const modelRows = computed(() => (ai.value?.byModel ?? []).map((m) => ({
  model: `${m.provider} / ${m.model}`, usd: usd(m.usd), calls: num(m.calls), errors: percent(m.errorPercent), blocked: percent(m.blockedPercent), avg: millis(m.avgMs), p95: millis(m.p95Ms),
})))
const modelColumns = [
  { key: 'model', label: 'Modelo' }, { key: 'usd', label: 'Coste (USD)' }, { key: 'calls', label: 'Llamadas' }, { key: 'errors', label: 'Errores' },
  { key: 'blocked', label: 'Bloqueos' }, { key: 'avg', label: 'Duración media' }, { key: 'p95', label: 'p95' },
]

const priceRows = computed(() => [
  ...(prices.value?.models ?? []).map((p) => ({
    model: `${p.provider} / ${p.model}`, price: `${usd(p.inputPerMillion)} entrada · ${usd(p.outputPerMillion)} salida (USD por millón de tokens)`, verified: dateOf(p.verifiedOn),
    notice: p.stale ? 'Revisar: más de 90 días o sin fecha' : 'Al día',
  })),
  ...(prices.value?.images ?? []).map((p) => ({
    model: `${p.provider} / ${p.model}`, price: `${usd(p.perImage)} por imagen (USD)`, verified: dateOf(p.verifiedOn), notice: p.stale ? 'Revisar: más de 90 días o sin fecha' : 'Al día',
  })),
])
const priceColumns = [{ key: 'model', label: 'Modelo' }, { key: 'price', label: 'Precio' }, { key: 'verified', label: 'Verificado' }, { key: 'notice', label: 'Aviso' }]

const loginRows = computed(() => (accounts.value?.logins ?? []).filter((l) => l.ok + l.failed + l.locked > 0).reverse().map((l) => ({ day: l.day, ok: num(l.ok), failed: num(l.failed), locked: num(l.locked) })))
const loginColumns = [{ key: 'day', label: 'Día' }, { key: 'ok', label: 'Correctos' }, { key: 'failed', label: 'Fallidos' }, { key: 'locked', label: 'Bloqueos' }]

const statusLabel: Record<string, string> = {
  ACTIVE: 'Activas', PENDING_VERIFICATION: 'Sin verificar', DEACTIVATED: 'Desactivadas',
  PENDING_GENERATION: 'En cola', GENERATING: 'Generándose', PAUSED: 'En pausa', READY: 'Listos', FAILED: 'Fallidos',
  UPLOADED: 'Subidos', PROCESSING: 'Procesándose', ERROR: 'Con error', PENDING: 'Pendientes', DONE: 'Hechas', BLOCKED: 'Bloqueadas', REMOVED: 'Quitadas',
}
const labelled = (counts: Record<string, number> | undefined) => Object.entries(counts ?? {}).map(([k, v]) => ({ label: statusLabel[k] ?? k, value: v }))
const failedWords = (n: number) => (n > 0 ? 'Con fallos' : 'Todo bien')
const tone = (n: number): 'ok' | 'bad' => (n > 0 ? 'bad' : 'ok')
</script>

<template>
  <section class="page wide owner" data-testid="owner-view">
    <h1>Panel del propietario</h1>
    <p class="muted">Solo cifras: aquí nunca se ve el contenido de nadie.</p>

    <div class="owner-toolbar">
      <div v-if="tab !== 'health'" class="owner-ranges" role="group" aria-label="Rango de días">
        <UiButton v-for="r in ranges" :key="r" :data-testid="`owner-range-${r}`" :aria-pressed="range === r ? 'true' : 'false'" :variant="range === r ? 'primary' : 'secondary'" @click="changeRange(r)">{{ r }} días</UiButton>
      </div>
      <UiButton data-testid="owner-refresh" :disabled="loading" @click="load">Actualizar</UiButton>
    </div>

    <UiState v-if="failed" state="error" :message="failed" data-testid="owner-error" />
    <UiState v-else-if="loading" state="loading" message="Cargando datos…" data-testid="owner-loading" />

    <UiTabs v-model="tab" :tabs="tabs" id-prefix="owner-tab" label="Secciones del panel">
      <template #ai>
        <div v-if="ai && !failed" class="owner-panel">
          <div class="owner-grid">
            <StatCard label="Hoy" :value="`${usd(ai.todayUsd)} USD`" data-testid="owner-stat-today" />
            <StatCard label="Este mes" :value="`${usd(ai.monthUsd)} USD`" data-testid="owner-stat-month" />
            <StatCard label="Proyección a fin de mes" :value="`${usd(ai.projection.usd)} USD`" data-testid="owner-stat-projection" />
            <StatCard label="Tope global" :value="percent(ai.globalLimit.percent)" :hint="`Tope de ${usd(ai.globalLimit.limitUsd)} USD al mes`" :tone="globalTone" :status="globalStatus" data-testid="owner-stat-global" />
            <StatCard :label="`Gasto en ${ai.range} días`" :value="`${usd(ai.rangeUsd)} USD`" />
            <StatCard label="Llamadas" :value="num(ai.totals.calls)" />
            <StatCard label="Errores" :value="percent(ai.totals.errorPercent)" />
            <StatCard label="Bloqueos" :value="percent(ai.totals.blockedPercent)" />
            <StatCard label="Duración media" :value="millis(ai.totals.avgMs)" />
            <StatCard label="Por curso" :value="`${usd(ai.costPerCourse)} USD`" hint="Coste medio de un curso" />
            <StatCard label="Por usuario activo" :value="`${usd(ai.costPerActiveUser)} USD`" />
          </div>
          <p v-if="!ai.projection.reliable" class="owner-note muted" data-testid="owner-projection-note">La proyección es poco fiable: hay menos de 3 días de datos este mes.</p>

          <LineChart :points="ai.daily.map((d) => ({ label: d.day, value: d.usd }))" title="Coste por día" unit="USD" />
          <div class="owner-by-model" data-testid="owner-by-model"><DataTable :columns="modelColumns" :rows="modelRows" caption="Gasto por modelo" /></div>
          <div class="owner-grid owner-grid--wide">
            <BarList :items="ai.byProvider.map((p) => ({ label: p.provider, value: p.usd }))" title="Por proveedor" unit="USD" />
            <BarList :items="ai.byTask.map((t) => ({ label: t.task, value: t.usd }))" title="Por tarea" unit="USD" />
            <BarList :items="ai.byUser.map((u) => ({ label: u.label ?? u.userId, value: u.usd }))" title="Quien más gasta" unit="USD" />
          </div>
          <p class="owner-note" data-testid="owner-benchmark">Benchmark (aparte, no cuenta en las cifras de arriba): {{ num(ai.benchmark.calls) }} llamadas, {{ usd(ai.benchmark.usd) }} USD.</p>
          <div v-if="prices" data-testid="owner-prices"><DataTable :columns="priceColumns" :rows="priceRows" caption="Precios vigentes" /></div>
        </div>
      </template>

      <template #users>
        <div v-if="accounts && !failed" class="owner-panel">
          <div class="owner-grid">
            <StatCard label="Activos hoy" :value="num(accounts.active.d1)" data-testid="owner-stat-active-1" />
            <StatCard label="Activos 7 días" :value="num(accounts.active.d7)" data-testid="owner-stat-active-7" />
            <StatCard label="Activos 30 días" :value="num(accounts.active.d30)" hint="Con al menos un inicio de sesión" data-testid="owner-stat-active-30" />
            <StatCard label="Sesiones abiertas" :value="num(accounts.openSessions)" data-testid="owner-stat-sessions" />
            <StatCard label="Adultos" :value="num(accounts.adults)" data-testid="owner-stat-adults" />
            <StatCard label="Menores" :value="num(accounts.minors)" data-testid="owner-stat-minors" />
          </div>
          <BarList :items="labelled(accounts.byStatus)" title="Cuentas por estado" />
          <LineChart :points="accounts.signupsDaily.map((d) => ({ label: d.day, value: d.count }))" title="Altas por día" unit="altas" />
          <DataTable v-if="loginRows.length" :columns="loginColumns" :rows="loginRows" caption="Inicios de sesión por día (solo los días con actividad)" />
          <p v-else class="muted" data-testid="owner-logins-empty">Sin inicios de sesión en este rango.</p>
        </div>
        <UserList ref="list" />
      </template>

      <template #usage>
        <div v-if="usage && !failed" class="owner-panel" data-testid="owner-usage">
          <div class="owner-grid">
            <StatCard label="Lecciones hechas" :value="num(usage.study.lessonsDone)" />
            <StatCard label="Intentos de ejercicio" :value="num(usage.study.exerciseAttempts)" />
            <StatCard label="Intentos de examen" :value="num(usage.study.examAttempts)" :hint="`${num(usage.study.examsPassed)} superados`" />
            <StatCard label="Preguntas al tutor" :value="num(usage.tutor.questions)" :hint="`${num(usage.tutor.blocked)} bloqueadas · ${num(usage.tutor.filtered)} filtradas`" />
            <StatCard label="Piezas hechas" :value="num(usage.pieces.done)" :hint="`${num(usage.pieces.failed)} fallidas · ${num(usage.pieces.pending)} pendientes`" />
            <StatCard label="Códigos creados" :value="num(usage.sharing.created)" :hint="`${num(usage.sharing.redeemed)} canjeados · ${num(usage.sharing.revoked)} revocados`" />
            <StatCard label="Cursos borrados" :value="num(usage.coursesDeleted)" data-testid="owner-usage-deleted" />
          </div>
          <div class="owner-grid owner-grid--wide">
            <BarList :items="labelled(usage.courses.byStatus)" title="Cursos por estado" />
            <BarList :items="labelled(usage.documents.byStatus)" title="Documentos por estado" />
            <BarList :items="labelled(usage.illustrations.byStatus)" title="Ilustraciones por estado" />
          </div>
          <LineChart :points="usage.courses.createdDaily.map((d) => ({ label: d.day, value: d.count }))" title="Cursos creados por día" unit="cursos" />
        </div>
      </template>

      <template #health>
        <div v-if="health && !failed" class="owner-panel" data-testid="owner-health">
          <div class="owner-grid">
            <StatCard label="Cursos en cola" :value="num(health.courses.pendingGeneration)" :hint="`${num(health.courses.generating)} generándose`" />
            <StatCard label="Piezas fallidas" :value="num(health.pieces.failed)" :hint="`${num(health.pieces.pending)} pendientes`" :tone="tone(health.pieces.failed)" :status="failedWords(health.pieces.failed)" />
            <StatCard label="Documentos con error" :value="num(health.documents.failed)" :hint="`${num(health.documents.pending)} pendientes`" :tone="tone(health.documents.failed)" :status="failedWords(health.documents.failed)" />
            <StatCard label="Ilustraciones fallidas" :value="num(health.illustrations.failed)" :hint="`${num(health.illustrations.pending)} pendientes`" :tone="tone(health.illustrations.failed)" :status="failedWords(health.illustrations.failed)" />
            <StatCard label="Contenido bloqueado (24 h)" :value="num(health.contentBlocks24h)" />
            <StatCard label="Tamaño de la base de datos" :value="megabytes(health.databaseBytes)" />
          </div>
          <DataTable :columns="[{ key: 'provider', label: 'Proveedor' }, { key: 'kind', label: 'Tipo de error' }, { key: 'count', label: 'Veces' }]"
                     :rows="health.aiErrors.map((e) => ({ provider: e.provider, kind: e.errorKind, count: num(e.count) }))" caption="Errores de IA de las últimas 24 horas" />
          <DataTable :columns="[{ key: 'name', label: 'Tabla' }, { key: 'rows', label: 'Filas (estimadas)' }]" :rows="health.tables.map((t) => ({ name: t.name, rows: num(t.rows) }))" caption="Tablas que más crecen" />
        </div>
      </template>
    </UiTabs>
  </section>
</template>
