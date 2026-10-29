<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, CircleX, Info, TriangleAlert } from 'lucide-vue-next'

type Tone = 'plain' | 'now' | 'ok' | 'accent' | 'warn' | 'bad' | 'info' | 'map' | 'material' | 'results' | 'owner'

const props = withDefaults(defineProps<{ tone?: Tone }>(), { tone: 'plain' })

// Un estado nunca se dice solo con color (F17, ADR-96): éxito, aviso, error e información llevan icono
const ICONS = { ok: CircleCheck, warn: TriangleAlert, bad: CircleX, info: Info } as const
const icon = computed(() => (props.tone in ICONS ? ICONS[props.tone as keyof typeof ICONS] : null))
</script>

<template>
  <span class="ui-tag" :class="`ui-tag--${tone}`"><component :is="icon" v-if="icon" :size="14" aria-hidden="true" /><slot /></span>
</template>
