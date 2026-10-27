<script setup lang="ts">
import { computed, useId } from 'vue'
import type { IllustrationOffer } from '@/api/client'

/**
 * El interruptor «Con ilustraciones» del asistente (F12). Solo se pinta si el servidor lo ofrece (hay proveedor de imagen y quien crea el curso es un adulto) y
 * enseña el coste estimado: precio por imagen y el máximo posible con el tope de temas de un curso. Apagado por defecto: el padre decide el valor.
 */
const props = defineProps<{ offer: IllustrationOffer | null; modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const id = useId()
const money = (n: number) => n.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const price = computed(() => money(props.offer?.pricePerImageUsd ?? 0))
const most = computed(() => money((props.offer?.pricePerImageUsd ?? 0) * (props.offer?.maxTopics ?? 0)))
</script>

<template>
  <div v-if="offer?.available" class="illustration-toggle" data-testid="illustration-toggle">
    <div class="check">
      <input :id="id" type="checkbox" :checked="modelValue" @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)" />
      <label :for="id">Con ilustraciones</label>
    </div>
    <p class="muted">Una imagen por tema, generada por IA después de crear el curso. El curso no espera por ellas.</p>
    <p class="muted" data-testid="illustration-cost">
      Estimación: {{ price }} USD por imagen; con {{ offer.maxTopics }} temas, como mucho {{ most }} USD (ese es el tope). Cuenta para tu gasto mensual de IA.
    </p>
  </div>
</template>
