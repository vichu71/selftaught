<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(defineProps<{ label: string; modelValue: string; error?: string; type?: string; hint?: string }>(), { type: 'text', error: '', hint: '' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const uid = useId()
const inputId = `field-${uid}`
const errorId = `${inputId}-error`
const hintId = `${inputId}-hint`
const describedBy = computed(() => [props.error ? errorId : '', props.hint ? hintId : ''].filter(Boolean).join(' ') || undefined)
</script>

<template>
  <div class="ui-field">
    <label :for="inputId">{{ label }}</label>
    <input
      :id="inputId"
      v-bind="$attrs"
      :type="type"
      :value="modelValue"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <p v-if="hint" :id="hintId" class="muted">{{ hint }}</p>
    <p v-if="error" :id="errorId" class="field-error" role="alert">{{ error }}</p>
  </div>
</template>

<script lang="ts">
export default { inheritAttrs: false }
</script>
