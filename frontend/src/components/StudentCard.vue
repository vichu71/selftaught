<script setup lang="ts">
import { CircleCheck, Moon } from 'lucide-vue-next'
import type { Student } from '@/api/client'
import UiButton from '@/ui/UiButton.vue'

// Un menor como tarjeta: su nombre, el estado dicho en palabras con icono, su usuario y las acciones como botones.
defineProps<{ student: Student }>()
const emit = defineEmits<{ reset: [id: string]; toggle: [id: string] }>()
</script>

<template>
  <li class="student-card" :data-testid="`student-${student.id}`">
    <div class="student-card-who">
      <strong>{{ student.displayName }}</strong>
      <span class="ui-tag" :class="student.status === 'ACTIVE' ? 'ui-tag--ok' : 'ui-tag--plain'" :data-testid="`student-status-${student.id}`">
        <CircleCheck v-if="student.status === 'ACTIVE'" :size="14" aria-hidden="true" />
        <Moon v-else :size="14" aria-hidden="true" />
        {{ student.status === 'ACTIVE' ? 'Activo' : 'De baja' }}
      </span>
      <span class="muted">usuario: {{ student.loginId }}</span>
    </div>
    <div class="student-card-actions">
      <RouterLink :to="`/alumnos/${student.id}`" class="ui-button ui-button--secondary" :data-testid="`student-activity-${student.id}`">Ver actividad</RouterLink>
      <UiButton :data-testid="`reset-${student.id}`" @click="emit('reset', student.id)">Cambiar contraseña</UiButton>
      <UiButton v-if="student.status === 'ACTIVE'" :data-testid="`deactivate-${student.id}`" @click="emit('toggle', student.id)">Dar de baja</UiButton>
      <UiButton v-else :data-testid="`reactivate-${student.id}`" @click="emit('toggle', student.id)">Reactivar</UiButton>
    </div>
  </li>
</template>
