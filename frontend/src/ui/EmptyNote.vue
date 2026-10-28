<script setup lang="ts">
import Mascot from '@/world/Mascot.vue'
import PlantIllustration from '@/world/PlantIllustration.vue'

// Un «todavía no hay nada» amable: la semilla de F11 (o la mascota) y una frase. La ilustración queda fuera del orden de lectura.
withDefaults(defineProps<{ message: string; art?: 'seed' | 'mascot' }>(), { art: 'seed' })
</script>

<template>
  <div class="empty-note" role="status">
    <Mascot v-if="art === 'mascot'" class="empty-note-art" />
    <PlantIllustration v-else stage="seed" class="empty-note-art" />
    <p>{{ message }}</p>
  </div>
</template>
