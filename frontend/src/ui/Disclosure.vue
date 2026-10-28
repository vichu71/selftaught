<script setup lang="ts">
import { useId } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

// Un bloque que se abre y se cierra con un botón (aria-expanded + aria-controls). La región se oculta con v-show, no con v-if:
// no se pierde lo escrito en ella. Los atributos (data-testid…) van al botón.
defineOptions({ inheritAttrs: false })
defineProps<{ label: string; open: boolean }>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()
const regionId = `disclosure-${useId()}`
</script>

<template>
  <div class="disclosure">
    <button type="button" class="ui-button disclosure-toggle" v-bind="$attrs" :aria-expanded="open ? 'true' : 'false'" :aria-controls="regionId" @click="emit('update:open', !open)">
      <ChevronDown :size="18" aria-hidden="true" :class="{ 'is-open': open }" />
      <span>{{ label }}</span>
    </button>
    <div v-show="open" :id="regionId" class="disclosure-region">
      <slot />
    </div>
  </div>
</template>
