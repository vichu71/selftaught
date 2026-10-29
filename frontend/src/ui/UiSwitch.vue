<script setup lang="ts">
import { SwitchRoot, SwitchThumb } from 'reka-ui'

// Interruptor accesible (Reka UI): role="switch" con aria-checked. El estado se dice siempre también con texto (lo pone quien lo usa) y con la posición del botón.
defineProps<{ modelValue: boolean; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <SwitchRoot class="ui-switch" :model-value="modelValue" :disabled="disabled" @update:model-value="$emit('update:modelValue', $event)">
    <SwitchThumb class="ui-switch-thumb" />
  </SwitchRoot>
</template>
