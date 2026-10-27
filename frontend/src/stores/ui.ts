import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * Estado de la interfaz que no es de ninguna pantalla. `restKey` sube con «Dejarlo por hoy»: la vista actual se monta de nuevo y
 * con ella se cierra todo lo que tenía abierto (paneles, diálogos, formularios), también si ya estabas en el Inicio.
 */
export const useUiStore = defineStore('ui', () => {
  const restKey = ref(0)

  function rest() {
    restKey.value += 1
  }

  return { restKey, rest }
})
