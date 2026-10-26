<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type GuardianAttempts, type GuardianCourse } from '@/api/client'
import GuardianAttemptsList from '@/components/GuardianAttempts.vue'

const route = useRoute()
const studentId = computed(() => String(route.params.id))
const courseId = computed(() => String(route.params.courseId))

const course = ref<GuardianCourse | null>(null)
const attempts = ref<GuardianAttempts | null>(null)
const loading = ref(true)
const notFound = ref(false)
const failed = ref(false)

onMounted(async () => {
  const [c, a] = await Promise.allSettled([api.getGuardianCourse(studentId.value, courseId.value), api.getGuardianAttempts(studentId.value, courseId.value)])
  if (c.status === 'fulfilled') course.value = c.value
  if (a.status === 'fulfilled') attempts.value = a.value
  const errors = [c, a].filter((r): r is PromiseRejectedResult => r.status === 'rejected').map((r) => r.reason)
  notFound.value = errors.some((e) => e instanceof ApiError && e.status === 404)
  failed.value = !notFound.value && errors.length > 0
  loading.value = false
})
</script>

<template>
  <div class="page wide" data-testid="guardian-course">
    <header class="topbar">
      <h1 v-if="course" data-testid="guardian-course-title">{{ course.title }}</h1>
      <h1 v-else>Curso del alumno</h1>
      <RouterLink :to="`/alumnos/${studentId}`" data-testid="guardian-back">Volver al alumno</RouterLink>
    </header>

    <p v-if="loading" class="muted" data-testid="guardian-loading">Cargando…</p>
    <p v-else-if="notFound" class="error" role="alert" data-testid="guardian-not-found">No encontramos este curso entre los de tu alumno.</p>
    <template v-else>
      <p v-if="failed" class="error" role="alert" data-testid="guardian-error">No se ha podido cargar todo. Inténtalo más tarde.</p>

      <section v-if="course" class="card">
        <h2>Avance <span v-if="course.overview.percent !== undefined" class="muted">· {{ course.overview.percent }} %</span></h2>
        <ol class="guardian-list">
          <li v-for="(topic, i) in course.overview.topics" :key="topic.id" :data-testid="`guardian-topic-${i}`">
            <strong>Tema {{ i + 1 }}</strong> · {{ topic.percent }} %
            <ul class="guardian-list">
              <li v-for="(lesson, j) in topic.lessons" :key="lesson.pieceId">
                <RouterLink v-if="lesson.status === 'DONE'" :to="`/alumnos/${studentId}/cursos/${courseId}/lecciones/${lesson.pieceId}`" :data-testid="`guardian-lesson-link-${lesson.pieceId}`">
                  Lección {{ j + 1 }}
                </RouterLink>
                <span v-else>Lección {{ j + 1 }}</span>
                <span class="muted"> · {{ lesson.done ? 'hecha' : lesson.status === 'DONE' ? 'sin hacer' : 'sin preparar' }}</span>
              </li>
              <li v-if="topic.exam">
                Examen:
                <span v-if="topic.exam.attempts === 0" class="muted">sin hacer</span>
                <span v-else>
                  {{ topic.exam.attempts }} {{ topic.exam.attempts === 1 ? 'intento' : 'intentos' }} · mejor nota {{ topic.exam.best }} % · última {{ topic.exam.last }} % ·
                  {{ topic.exam.passed ? 'tema superado' : 'sin superar' }}
                </span>
              </li>
            </ul>
          </li>
        </ol>
      </section>

      <GuardianAttemptsList v-if="attempts" :attempts="attempts" />
    </template>
  </div>
</template>
