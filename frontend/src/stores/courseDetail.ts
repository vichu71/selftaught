import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api, type CourseDetail } from '@/api/client'

export const POLL_MS = 3000
const ACTIVE = new Set(['PENDING_GENERATION', 'GENERATING'])

/**
 * Un curso con su temario y progreso. Mientras se prepara (en cola o generándose) se vuelve a pedir cada 3 segundos; en cuanto se para
 * (listo, en pausa o fallado) o el usuario sale de la pantalla, deja de preguntar.
 */
export const useCourseDetailStore = defineStore('courseDetail', () => {
  const course = ref<CourseDetail | null>(null)
  const notFound = ref(false)
  const failed = ref(false)
  const resuming = ref(false)
  const resumeRequested = ref(false)
  const resumeError = ref('')

  let courseId = ''
  let timer: ReturnType<typeof setTimeout> | undefined
  let generation = 0

  const polling = computed(() => !!course.value && ACTIVE.has(course.value.status))

  function stop() {
    generation++
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  function schedule(token: number) {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
    if (token !== generation || !polling.value) return
    timer = setTimeout(() => void refresh(token), POLL_MS)
  }

  async function refresh(token: number) {
    try {
      const fresh = await api.getCourse(courseId)
      if (token !== generation) return
      course.value = fresh
      resumeRequested.value = false
      failed.value = false
    } catch (e) {
      if (token !== generation) return
      if (!course.value) {
        if (e instanceof ApiError && e.status === 404) notFound.value = true
        else failed.value = true
      }
    }
    schedule(token)
  }

  /** Carga el curso y, si se está preparando, empieza a preguntar por su progreso. */
  async function start(id: string) {
    stop()
    courseId = id
    course.value = null
    notFound.value = false
    failed.value = false
    resuming.value = false
    resumeRequested.value = false
    resumeError.value = ''
    const token = generation
    await refresh(token)
  }

  /** Pide continuar un curso en pausa. Una vez aceptado, el botón no se puede volver a pulsar hasta que llegue el siguiente estado. */
  async function resume() {
    if (resuming.value || resumeRequested.value || !course.value) return
    resuming.value = true
    resumeError.value = ''
    const token = generation
    try {
      const updated = await api.resumeCourse(courseId)
      if (token !== generation) return
      course.value = updated
      resumeRequested.value = true
      schedule(token)
    } catch (e) {
      resumeError.value =
        e instanceof ApiError && e.code === 'RESUME_TOO_SOON'
          ? 'Acabas de reanudar este curso. Espera un minuto e inténtalo de nuevo.'
          : 'No se pudo reanudar el curso. Inténtalo de nuevo.'
    } finally {
      resuming.value = false
    }
  }

  return { course, notFound, failed, resuming, resumeRequested, resumeError, polling, start, stop, resume }
})
