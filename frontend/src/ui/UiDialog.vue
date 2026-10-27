<script setup lang="ts">
import { DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import UiButton from './UiButton.vue'

defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    description?: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'primary' | 'danger'
    busy?: boolean
    /** F14: el diálogo no se cierra al confirmar (lo cierra quien lo usa cuando la acción termina bien) y puede enseñar un error dentro. */
    keepOpen?: boolean
    confirmDisabled?: boolean
    confirmTestid?: string
    cancelTestid?: string
  }>(),
  { description: '', confirmLabel: 'Aceptar', cancelLabel: 'Cancelar', tone: 'primary', busy: false, keepOpen: false, confirmDisabled: false, confirmTestid: 'dialog-confirm', cancelTestid: 'dialog-cancel' },
)
const emit = defineEmits<{ 'update:modelValue': [open: boolean]; confirm: [] }>()
</script>

<template>
  <DialogRoot :open="modelValue" @update:open="(v) => emit('update:modelValue', v)">
    <DialogPortal>
      <DialogOverlay class="ui-dialog-overlay" />
      <DialogContent class="ui-dialog" v-bind="$attrs">
        <DialogTitle as="h2">{{ title }}</DialogTitle>
        <DialogDescription v-if="description" class="muted">{{ description }}</DialogDescription>
        <slot />
        <div class="ui-dialog-actions">
          <UiButton :data-testid="cancelTestid" :disabled="busy" @click="emit('update:modelValue', false)">{{ cancelLabel }}</UiButton>
          <UiButton :data-testid="confirmTestid" :variant="tone" :disabled="busy || confirmDisabled" @click="emit('confirm'); if (!keepOpen) emit('update:modelValue', false)">{{ confirmLabel }}</UiButton>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
