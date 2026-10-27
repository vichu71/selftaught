<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { OwnerUser } from '@/api/client'
import UiDialog from '@/ui/UiDialog.vue'
import { usd } from './format'
import type { OwnerAction } from './types'



const props = defineProps<{ modelValue: boolean; action: OwnerAction; user: OwnerUser | null; busy?: boolean; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [open: boolean]; confirm: [limitUsd: number | null] }>()

const raw = ref('')
watch(() => [props.modelValue, props.action, props.user?.id], () => { raw.value = props.user ? String(props.user.limitUsd) : '' })

/** Un tope válido: de 0 a 500 con dos decimales como mucho (el servidor lo vuelve a comprobar). */
const limit = computed<number | null>(() => {
  const text = raw.value.trim().replace(',', '.')
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(text)) return null
  const value = Number(text)
  return value >= 0 && value <= 500 ? value : null
})
const spentMore = computed(() => props.action === 'limit' && limit.value !== null && props.user !== null && limit.value < props.user.monthSpendUsd)

const who = computed(() => (props.user ? (props.user.kind === 'ADULT' ? (props.user.displayName ?? '') : (props.user.loginId ?? '')) : ''))
const copy = computed(() => ({
  deactivate: { title: 'Desactivar la cuenta', tone: 'danger' as const, confirm: 'Desactivar', text: 'No podrá entrar y se cerrarán sus sesiones. Se puede reactivar cuando quieras. Si es un adulto con menores a su cargo, los menores siguen activos, pero su adulto no podrá ver su panel.' },
  reactivate: { title: 'Reactivar la cuenta', tone: 'primary' as const, confirm: 'Reactivar', text: 'Podrá volver a entrar con su contraseña de siempre.' },
  revoke: { title: 'Cerrar sus sesiones', tone: 'danger' as const, confirm: 'Cerrar sesiones', text: 'Tendrá que iniciar sesión otra vez en todos sus dispositivos. La cuenta sigue activa.' },
  limit: { title: 'Tope mensual de IA', tone: 'primary' as const, confirm: 'Guardar tope', text: 'Máximo que puede gastar este mes, de 0 a 500 USD. Con 0 no podrá usar la IA. Corta siempre al llegar.' },
  clear: { title: 'Volver al tope por defecto', tone: 'primary' as const, confirm: 'Quitar tope', text: 'Dejará de tener un tope propio y valdrá el de por defecto (menor o adulto).' },
}[props.action]))
</script>

<template>
  <UiDialog
    :model-value="modelValue"
    :title="copy.title"
    :tone="copy.tone"
    :busy="busy"
    keep-open
    :confirm-disabled="action === 'limit' && limit === null"
    :confirm-label="copy.confirm"
    confirm-testid="owner-action-confirm"
    cancel-testid="owner-action-cancel"
    data-testid="owner-action-dialog"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @confirm="emit('confirm', action === 'limit' ? limit : null)"
  >
    <p v-if="user"><strong>{{ who }}</strong></p>
    <p>{{ copy.text }}</p>
    <div v-if="action === 'limit'">
      <label for="owner-limit-input">Tope en USD</label>
      <input id="owner-limit-input" v-model="raw" type="text" inputmode="decimal" autocomplete="off" data-testid="owner-limit-input" />
      <p v-if="limit === null" class="error" role="alert">Escribe un número de 0 a 500, con dos decimales como mucho.</p>
      <p v-if="spentMore && user" class="owner-warning" role="status" data-testid="owner-limit-warning">Esta persona ya ha gastado {{ usd(user.monthSpendUsd) }} USD este mes: con ese tope se quedará sin IA ahora mismo.</p>
    </div>
    <p v-if="error" class="error" role="alert" data-testid="owner-action-error">{{ error }}</p>
  </UiDialog>
</template>
