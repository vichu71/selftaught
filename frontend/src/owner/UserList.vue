<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, api, type OwnerUser, type OwnerUsersPage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import UiButton from '@/ui/UiButton.vue'
import UiState from '@/ui/UiState.vue'
import UiTag from '@/ui/UiTag.vue'
import { dateTimeOf, usd } from '@/ui/format'
import type { OwnerAction } from './types'
import UserActionDialog from './UserActionDialog.vue'

const PAGE_SIZE = 25
const auth = useAuthStore()

const kind = ref('')
const status = ref('')
const sort = ref('created')
const dir = ref<'asc' | 'desc'>('desc')
const page = ref(0)
const data = ref<OwnerUsersPage | null>(null)
const loading = ref(true)
const failed = ref('')

const dialogOpen = ref(false)
const action = ref<OwnerAction>('deactivate')
const target = ref<OwnerUser | null>(null)
const acting = ref(false)
const actionError = ref('')

const total = computed(() => data.value?.total ?? 0)
const lastPage = computed(() => Math.max(0, Math.ceil(total.value / PAGE_SIZE) - 1))
const statusText: Record<string, string> = { ACTIVE: 'Activa', PENDING_VERIFICATION: 'Sin verificar', DEACTIVATED: 'Desactivada' }

function tooMany(e: unknown): boolean {
  return e instanceof ApiError && e.status === 429
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    data.value = await api.ownerUsers({ kind: kind.value || undefined, status: status.value || undefined, sort: sort.value, dir: dir.value, page: page.value, size: PAGE_SIZE })
  } catch (e) {
    failed.value = tooMany(e) ? 'Demasiadas consultas. Espera un poco antes de volver a pedir datos.' : 'No se pudieron cargar los usuarios.'
  } finally {
    loading.value = false
  }
}

function refilter() {
  page.value = 0
  void load()
}
function go(delta: number) {
  page.value = Math.min(lastPage.value, Math.max(0, page.value + delta))
  void load()
}
function toggleDir() {
  dir.value = dir.value === 'desc' ? 'asc' : 'desc'
  refilter()
}

function open(next: OwnerAction, user: OwnerUser) {
  action.value = next
  target.value = user
  actionError.value = ''
  dialogOpen.value = true
}

async function applyAction(limitUsd: number | null) {
  const user = target.value
  if (acting.value || !user) return
  acting.value = true
  actionError.value = ''
  try {
    if (action.value === 'deactivate') await api.ownerDeactivate(user.id)
    else if (action.value === 'reactivate') await api.ownerReactivate(user.id)
    else if (action.value === 'revoke') await api.ownerRevoke(user.id)
    else if (action.value === 'limit' && limitUsd !== null) await api.ownerSetLimit(user.id, limitUsd)
    else if (action.value === 'clear') await api.ownerClearLimit(user.id)
    dialogOpen.value = false
    await load()
  } catch (e) {
    actionError.value = tooMany(e) ? 'Demasiadas acciones. Espera un poco antes de seguir.' : 'No se pudo hacer el cambio. Inténtalo de nuevo.'
  } finally {
    acting.value = false
  }
}

const isMe = (user: OwnerUser) => user.id === auth.user?.id
const nameOf = (user: OwnerUser) => (user.kind === 'ADULT' ? (user.displayName ?? '') : (user.loginId ?? ''))

onMounted(load)
defineExpose({ reload: load })
</script>

<template>
  <section class="owner-users" data-testid="owner-users">
    <h3>Usuarios</h3>
    <div class="owner-filters">
      <label>Tipo
        <select v-model="kind" data-testid="owner-filter-kind" @change="refilter">
          <option value="">Todos</option><option value="ADULT">Adultos</option><option value="MINOR">Menores</option>
        </select>
      </label>
      <label>Estado
        <select v-model="status" data-testid="owner-filter-status" @change="refilter">
          <option value="">Todos</option><option value="ACTIVE">Activas</option><option value="PENDING_VERIFICATION">Sin verificar</option><option value="DEACTIVATED">Desactivadas</option>
        </select>
      </label>
      <label>Ordenar por
        <select v-model="sort" data-testid="owner-sort" @change="refilter">
          <option value="created">Alta</option><option value="spend">Gasto del mes</option><option value="activity">Última actividad</option>
        </select>
      </label>
      <UiButton data-testid="owner-sort-dir" :aria-label="dir === 'desc' ? 'Orden descendente' : 'Orden ascendente'" @click="toggleDir">{{ dir === 'desc' ? '↓ Mayor primero' : '↑ Menor primero' }}</UiButton>
    </div>

    <UiState v-if="failed" state="error" :message="failed" data-testid="owner-users-error" />
    <UiState v-else-if="loading && !data" state="loading" message="Cargando usuarios…" />
    <p v-else-if="data && data.items.length === 0" class="muted" data-testid="owner-users-empty">No hay usuarios con esos filtros.</p>
    <ul v-else-if="data" class="owner-user-list">
      <li v-for="u in data.items" :key="u.id" class="owner-user" :data-testid="`owner-user-${u.id}`">
        <div class="owner-user-who">
          <strong>{{ nameOf(u) }}</strong>
          <span v-if="u.kind === 'ADULT' && u.email" class="muted">{{ u.email }}</span>
          <UiTag :tone="u.status === 'ACTIVE' ? 'ok' : 'plain'">{{ u.kind === 'MINOR' ? 'Menor' : 'Adulto' }} · {{ statusText[u.status] ?? u.status }}</UiTag>
        </div>
        <p class="owner-user-facts muted">
          Alta {{ dateTimeOf(u.createdAt) }} · Última actividad {{ dateTimeOf(u.lastActivityAt) }} · Este mes {{ usd(u.monthSpendUsd) }} de {{ usd(u.limitUsd) }} USD{{ u.limitIsCustom ? ' (tope propio)' : '' }} · {{ u.courses }} cursos
        </p>
        <div class="owner-user-actions">
          <UiButton v-if="!isMe(u) && u.status === 'ACTIVE'" :data-testid="`owner-user-${u.id}-deactivate`" @click="open('deactivate', u)">Desactivar</UiButton>
          <UiButton v-if="u.status === 'DEACTIVATED'" :data-testid="`owner-user-${u.id}-reactivate`" @click="open('reactivate', u)">Reactivar</UiButton>
          <UiButton v-if="!isMe(u)" :data-testid="`owner-user-${u.id}-revoke`" @click="open('revoke', u)">Cerrar sesiones</UiButton>
          <UiButton :data-testid="`owner-user-${u.id}-limit`" @click="open('limit', u)">Tope…</UiButton>
          <UiButton v-if="u.limitIsCustom" :data-testid="`owner-user-${u.id}-limit-clear`" @click="open('clear', u)">Tope por defecto</UiButton>
        </div>
      </li>
    </ul>

    <div class="owner-pager">
      <UiButton data-testid="owner-users-prev" :disabled="page === 0 || loading" @click="go(-1)">Anterior</UiButton>
      <span data-testid="owner-users-total">{{ total }} usuarios · página {{ page + 1 }} de {{ lastPage + 1 }}</span>
      <UiButton data-testid="owner-users-next" :disabled="page >= lastPage || loading" @click="go(1)">Siguiente</UiButton>
    </div>

    <UserActionDialog v-model="dialogOpen" :action="action" :user="target" :busy="acting" :error="actionError" @confirm="applyAction" />
  </section>
</template>
