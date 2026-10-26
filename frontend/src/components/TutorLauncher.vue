<script setup lang="ts">
import { nextTick, ref, useId } from 'vue'
import { MessageCircle, X } from 'lucide-vue-next'

const open = ref(false)
const panel = ref<HTMLElement | null>(null)
const button = ref<HTMLButtonElement | null>(null)
const panelId = `tutor-panel-${useId()}`

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    panel.value?.querySelector<HTMLElement>('textarea, input')?.focus()
  }
}

function close() {
  open.value = false
  button.value?.focus()
}
</script>

<template>
  <div class="tutor-launcher">
    <!-- El contenido sigue montado al cerrar: así no se pierde lo que el alumno estaba escribiendo -->
    <section v-show="open" :id="panelId" ref="panel" class="tutor-panel" data-testid="tutor-panel" aria-label="Tutor de IA" @keydown.esc="close">
      <button type="button" class="tutor-close" data-testid="tutor-close" aria-label="Cerrar el tutor" @click="close">
        <X :size="20" aria-hidden="true" />
      </button>
      <slot />
    </section>
    <button
      ref="button"
      type="button"
      class="tutor-fab"
      data-testid="tutor-open"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="panelId"
      @click="toggle"
    >
      <MessageCircle :size="22" aria-hidden="true" />
      <span>Pregunta al tutor</span>
    </button>
  </div>
</template>
