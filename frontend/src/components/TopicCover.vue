<script setup lang="ts">
import { ref, watch } from 'vue'
import { topicImagePath, type TopicImage } from '@/api/client'
import PlantIllustration from '@/world/PlantIllustration.vue'

/**
 * La ilustración de portada de un tema (F12). Solo pide la imagen al propio origen (`/api/...`) y con el texto alternativo que manda el servidor, que es texto:
 * nunca se pinta como HTML. Si la imagen no está (pendiente, fallida, bloqueada, quitada) o no carga, se ve la planta: ni mensajes de error ni nada roto.
 * `title` se declara para que el título del tema (texto de la IA o del usuario) no acabe como atributo del elemento; no se pinta.
 */
const props = defineProps<{ courseId: string; topicId: string; title?: string; image?: TopicImage | null }>()

const broken = ref(false)
// Una imagen nueva (regenerada) vuelve a intentarse aunque la anterior no hubiera cargado
watch(() => [props.image?.status, props.image?.alt], () => { broken.value = false })
</script>

<template>
  <figure class="topic-cover" data-testid="topic-cover">
    <img
      v-if="image?.status === 'DONE' && !broken"
      class="topic-cover-img"
      :src="topicImagePath(courseId, topicId)"
      :alt="image.alt ?? ''"
      loading="lazy"
      decoding="async"
      @error="broken = true"
    />
    <PlantIllustration v-else class="topic-cover-plant" stage="sprout" />
    <figcaption v-if="image?.status === 'DONE' && !broken" class="topic-cover-label">Ilustración generada por IA</figcaption>
    <figcaption v-else-if="image?.status === 'PENDING'" class="topic-cover-label" role="status">Preparándose…</figcaption>
  </figure>
</template>
