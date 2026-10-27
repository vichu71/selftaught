<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, OctagonX, TriangleAlert } from 'lucide-vue-next'
import { usd } from '@/ui/format'
import UiProgress from '@/ui/UiProgress.vue'

// Medidor del tope mensual de IA: la barra nunca pasa del 100 % y el estado se dice con palabras e icono, no solo con color.
const props = defineProps<{ label: string; spendUsd?: number; limitUsd?: number; reached?: boolean }>()

const spend = computed(() => (Number.isFinite(props.spendUsd) ? (props.spendUsd as number) : 0))
const hasLimit = computed(() => props.limitUsd !== undefined && props.limitUsd !== null && Number.isFinite(props.limitUsd))
const limit = computed(() => (hasLimit.value ? (props.limitUsd as number) : 0))
const done = computed(() => Boolean(props.reached) || (hasLimit.value && spend.value >= limit.value))
const percent = computed(() => (limit.value > 0 ? Math.min(100, Math.round((spend.value / limit.value) * 100)) : done.value ? 100 : 0))
const tone = computed<'ok' | 'warn' | 'bad'>(() => (done.value ? 'bad' : percent.value >= 80 ? 'warn' : 'ok'))
const state = computed(() => ({ ok: 'Vas bien', warn: 'Cerca del tope', bad: 'Tope alcanzado' })[tone.value])
</script>

<template>
  <div class="tope-meter" :class="`tope-meter--${tone}`">
    <p class="tope-meter-label">{{ label }}</p>
    <p class="tope-meter-figures">
      <template v-if="hasLimit">{{ usd(spend) }} de {{ usd(limit) }} USD</template>
      <template v-else>{{ usd(spend) }} USD gastados</template>
    </p>
    <UiProgress :value="percent" :label="label" />
    <p class="tope-meter-state" data-testid="tope-state">
      <CircleCheck v-if="tone === 'ok'" :size="16" aria-hidden="true" />
      <TriangleAlert v-else-if="tone === 'warn'" :size="16" aria-hidden="true" />
      <OctagonX v-else :size="16" aria-hidden="true" />
      <span>{{ state }}</span>
    </p>
  </div>
</template>
