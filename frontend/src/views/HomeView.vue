<script setup lang="ts">
import { ref } from 'vue'
import { api, type Course } from '@/api/client'
import ContinueCard from '@/components/ContinueCard.vue'
import CoursesPanel from '@/components/CoursesPanel.vue'
import StudentsPanel from '@/components/StudentsPanel.vue'
import { nextStop, type NextStop } from '@/map/layoutMap'
import { useAuthStore } from '@/stores/auth'
import { useStudyStore } from '@/stores/study'

const auth = useAuthStore()
const study = useStudyStore()
const stop = ref<NextStop | null>(null)
const stopCourse = ref('')

/**
 * «Sigue donde lo dejaste» (ADR-67): la siguiente lección lista del curso terminado con más avance que no está al 100 %.
 * Son datos de comodidad: si algo falla, el Inicio funciona igual sin esta tarjeta.
 */
async function pickContinue(courses: Course[]) {
  const course = courses
    .filter((c) => c.status === 'READY' && (study.percentByCourse[c.id] ?? 0) < 100)
    .sort((a, b) => (study.percentByCourse[b.id] ?? 0) - (study.percentByCourse[a.id] ?? 0))[0]
  if (!course) return
  try {
    const [detail, overview] = await Promise.all([api.getCourse(course.id), api.getStudy(course.id)])
    const next = nextStop(course.id, detail?.outline ?? [], overview ?? null)
    if (next) {
      stop.value = next
      stopCourse.value = course.title
    }
  } catch {
    stop.value = null
  }
}
</script>

<template>
  <div class="page wide" data-testid="app-shell">
    <header class="topbar">
      <h1 data-testid="welcome">Hola, {{ auth.user?.displayName }}</h1>
    </header>

    <p v-if="auth.user?.kind === 'MINOR'" class="notice" data-testid="guardian-notice">
      Tu adulto tutor puede ver tus cursos, tu progreso, tus respuestas e intentos de ejercicios y exámenes y tus conversaciones con el tutor de IA.
    </p>

    <ContinueCard v-if="stop" :stop="stop" :course-title="stopCourse" />

    <CoursesPanel @loaded="pickContinue" />

    <section class="card">
      <h2>Tu espacio de estudio</h2>
      <p><RouterLink to="/material" data-testid="material-link">Mi material</RouterLink> · sube tus apuntes (PDF, Word o texto).</p>
      <p><RouterLink to="/codigo" data-testid="redeem-link">Tengo un código</RouterLink> · recibe tu propia copia de un curso que te han compartido.</p>
      <p><RouterLink to="/resultados" data-testid="results-link">Resultados</RouterLink> · {{ auth.user?.kind === 'MINOR' ? 'cómo van tus cursos.' : 'cómo van tus cursos y cuánto se ha gastado.' }}</p>
    </section>

    <StudentsPanel v-if="auth.isTutor" />
  </div>
</template>
