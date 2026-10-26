<script setup lang="ts">
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'

withDefaults(defineProps<{ tabs: { id: string; label: string }[]; modelValue: string; idPrefix?: string; label?: string }>(), { idPrefix: 'tab', label: 'Secciones' })
const emit = defineEmits<{ 'update:modelValue': [id: string] }>()
</script>

<template>
  <TabsRoot :model-value="modelValue" @update:model-value="(v) => emit('update:modelValue', String(v))">
    <TabsList class="ui-tabs-list" :aria-label="label">
      <TabsTrigger v-for="t in tabs" :key="t.id" :value="t.id" class="ui-tab" :data-testid="`${idPrefix}-${t.id}`">{{ t.label }}</TabsTrigger>
    </TabsList>
    <TabsContent v-for="t in tabs" :key="t.id" :value="t.id"><slot :name="t.id" /></TabsContent>
  </TabsRoot>
</template>
