<script setup lang="ts">
import { computed } from 'vue'
import type { CourseResult, CourseStatus } from '@/api/client'
import UiProgress from '@/ui/UiProgress.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'
import { STAGE_LABEL, plantStage } from '@/world/plantStage'

// Un curso como tarjeta de jardín: su planta (la fase sale de las piezas hechas), su barra de piezas, su estado y, si es un adulto, lo que costó.
const props = defineProps<{ course: CourseResult; showMoney: boolean }>()

const labels: Record<CourseStatus, string> = {
  PENDING_GENERATION: 'En cola',
  GENERATING: 'Preparándose',
  PAUSED: 'En pausa',
  READY: 'Listo',
  FAILED: 'No se pudo preparar',
}
const money = new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 4 })

const percent = computed(() => (props.course.total > 0 ? Math.round((props.course.done * 100) / props.course.total) : undefined))
const stage = computed(() => plantStage(percent.value))
const stageText = computed(() => (percent.value === undefined ? 'Preparándose' : STAGE_LABEL[stage.value]))
</script>

<template>
  <li class="result-card" :data-testid="`result-${course.id}`">
    <PlantIllustration :stage="stage" :label="`${stageText}: ${course.title}`" />
    <RouterLink :to="`/cursos/${course.id}`" :data-testid="`result-link-${course.id}`">{{ course.title }}</RouterLink>
    <span class="status">{{ labels[course.status] }}</span>
    <span class="plant-stage">{{ stageText }}</span>
    <template v-if="course.total > 0">
      <UiProgress :value="percent ?? 0" :label="`Piezas hechas de ${course.title}`" />
      <p class="result-numbers">{{ course.done }} de {{ course.total }} piezas</p>
    </template>
    <p v-if="course.failed > 0 || course.pending > 0" class="result-problems">
      <span v-if="course.failed > 0" class="result-failed" :data-testid="`result-failed-${course.id}`">{{ course.failed }} con error</span>
      <span v-if="course.pending > 0">{{ course.pending }} pendientes</span>
    </p>
    <p class="muted result-numbers">
      {{ course.calls }} llamadas<template v-if="showMoney && course.costUsd !== undefined"> · <span :data-testid="`result-cost-${course.id}`">{{ money.format(course.costUsd) }} USD</span></template>
    </p>
  </li>
</template>
