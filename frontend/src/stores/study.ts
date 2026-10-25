import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, type StudyOverview } from '@/api/client'

/**
 * El avance de estudio, solo lectura: el porcentaje de cada curso («Tus cursos») y el detalle de un curso. Son datos de comodidad: si no se pueden cargar,
 * la pantalla funciona igual sin ellos (el servidor es quien calcula el progreso, nunca el cliente).
 */
export const useStudyStore = defineStore('study', () => {
  const percentByCourse = ref<Record<string, number>>({})
  const overview = ref<StudyOverview | null>(null)

  async function loadProgress() {
    try {
      const list = await api.getStudyProgress()
      const map: Record<string, number> = {}
      for (const item of list ?? []) if (typeof item.percent === 'number') map[item.courseId] = item.percent
      percentByCourse.value = map
    } catch {
      percentByCourse.value = {}
    }
  }

  async function loadOverview(courseId: string) {
    try {
      overview.value = (await api.getStudy(courseId)) ?? null
    } catch {
      overview.value = null
    }
  }

  function reset() {
    overview.value = null
  }

  return { percentByCourse, overview, loadProgress, loadOverview, reset }
})
