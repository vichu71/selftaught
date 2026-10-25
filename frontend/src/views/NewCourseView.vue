<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { BLOCKED_TEXT, MAX_DOCUMENTS, useCourseStore } from '@/stores/course'
import { useMaterialStore } from '@/stores/material'

const course = useCourseStore()
const material = useMaterialStore()
const router = useRouter()

const statusLabels = { UPLOADED: 'subido', PROCESSING: 'procesando…', READY: 'listo', ERROR: 'con error' } as const

const chosenAnswers = computed(() =>
  course.questions.map((q) => ({ question: q.text, label: q.options.find((o) => o.id === course.answerFor(q))?.label ?? '' })),
)

async function chooseMaterial(yes: boolean) {
  course.wantsMaterial = yes
  course.docsError = ''
  if (yes) await material.load()
}

async function accept() {
  const id = await course.accept()
  if (id) {
    course.reset()
    await router.push(`/cursos/${encodeURIComponent(id)}`)
  }
}

function restart() {
  course.reset()
}
</script>

<template>
  <main class="page" data-testid="new-course-view">
    <header class="topbar">
      <h1>Nuevo curso</h1>
      <RouterLink to="/" data-testid="cancel-home">Volver al inicio</RouterLink>
    </header>

    <!-- Paso 1: el tema -->
    <section v-if="course.step === 'topic'" class="card" data-testid="step-topic">
      <form novalidate @submit.prevent="course.submitTopic">
        <label for="topic">¿Qué quieres estudiar?</label>
        <input
          id="topic" v-model="course.topic" data-testid="topic-input" type="text" autocomplete="off" maxlength="400"
          placeholder="Por ejemplo: inglés de 2º de la ESO" aria-describedby="topic-help"
        />
        <p id="topic-help" class="muted">De 3 a 200 letras.</p>
        <p v-if="course.topicError" class="field-error" role="alert" data-testid="topic-error">{{ course.topicError }}</p>
        <button type="button" class="primary" data-testid="topic-next" @click="course.submitTopic">Continuar</button>
      </form>
    </section>

    <!-- Paso 2: ¿tienes información? -->
    <section v-else-if="course.step === 'material'" class="card" data-testid="step-material">
      <h2>¿Tienes información que aportar?</h2>
      <p class="muted">Puedes usar apuntes o documentos que hayas subido a «Mi material».</p>
      <div class="choice">
        <button type="button" data-testid="material-yes" :aria-pressed="course.wantsMaterial === true" @click="chooseMaterial(true)">Sí</button>
        <button type="button" data-testid="material-no" :aria-pressed="course.wantsMaterial === false" @click="chooseMaterial(false)">No</button>
      </div>

      <div v-if="course.wantsMaterial" class="documents-choice">
        <p v-if="material.own.length === 0" class="muted">Todavía no tienes documentos. <RouterLink to="/material">Sube uno en «Mi material»</RouterLink>.</p>
        <ul class="documents">
          <li v-for="d in material.own" :key="d.id" class="document">
            <label class="check">
              <input
                v-model="course.documentIds" type="checkbox" :value="d.id" :data-testid="`doc-option-${d.id}`" :disabled="d.status !== 'READY'"
              />
              <span>{{ d.name }}</span>
              <span class="badge">{{ statusLabels[d.status] }}</span>
            </label>
          </li>
        </ul>
        <p class="muted">Elige hasta {{ MAX_DOCUMENTS }}.</p>
        <p v-if="course.docsError" class="field-error" role="alert" data-testid="docs-error">{{ course.docsError }}</p>
      </div>

      <p v-if="course.thinking" class="muted" role="status" data-testid="thinking">Pensando tu curso…</p>
      <div v-if="course.intakeError" class="notice" role="alert" data-testid="intake-error">
        <p>{{ course.intakeError }}</p>
        <button type="button" data-testid="retry" @click="course.askAssistant">Intentarlo de nuevo</button>
      </div>

      <div class="nav">
        <button type="button" data-testid="back" @click="course.back">Atrás</button>
        <button type="button" class="primary" data-testid="material-next" :disabled="course.wantsMaterial === null || course.thinking" @click="course.askAssistant">
          Continuar
        </button>
      </div>
    </section>

    <!-- Paso 3: preguntas -->
    <section v-else-if="course.step === 'questions'" class="card" data-testid="step-questions">
      <h2>Una pregunta rápida</h2>
      <fieldset v-for="q in course.questions" :key="q.id" class="question" :data-testid="`question-${q.id}`">
        <legend>{{ q.text }}</legend>
        <label v-for="o in q.options" :key="o.id" class="check">
          <input v-model="course.chosen[q.id]" type="radio" :name="`q-${q.id}`" :value="o.id" :data-testid="`option-${q.id}-${o.id}`" />
          <span>{{ o.label }}</span>
        </label>
      </fieldset>
      <div class="nav">
        <button type="button" data-testid="back" @click="course.back">Atrás</button>
        <button type="button" class="primary" data-testid="questions-next" @click="course.toSummary">Continuar</button>
      </div>
    </section>

    <!-- Resumen -->
    <section v-else-if="course.step === 'summary'" class="card" data-testid="summary">
      <h2>Esto es lo que vamos a montar</h2>
      <p>Un curso de <strong>«{{ course.topic }}»</strong>.</p>
      <ul v-if="chosenAnswers.length > 0">
        <li v-for="(a, i) in chosenAnswers" :key="i"><span class="muted">{{ a.question }}</span> <strong>{{ a.label }}</strong></li>
      </ul>
      <p v-if="course.wantsMaterial && course.documentIds.length > 0" class="muted">
        Con {{ course.documentIds.length }} {{ course.documentIds.length === 1 ? 'documento tuyo' : 'documentos tuyos' }}.
      </p>
      <p class="muted">Generado por IA.</p>

      <p v-if="course.createError" class="error" role="alert" data-testid="create-error">{{ course.createError }}</p>
      <button v-if="course.expired" type="button" data-testid="restart" @click="restart">Empezar de nuevo</button>
      <div class="nav">
        <button type="button" data-testid="back" @click="course.back">Atrás</button>
        <button type="button" class="primary" data-testid="accept" :disabled="course.creating" @click="accept">Aceptar</button>
      </div>
    </section>

    <!-- Tema bloqueado para un menor -->
    <section v-else-if="course.step === 'blocked'" class="card" data-testid="step-blocked">
      <p class="notice" role="alert" data-testid="blocked-message">{{ BLOCKED_TEXT }}</p>
      <button type="button" data-testid="blocked-back" @click="course.backFromBlocked">Volver</button>
    </section>
  </main>
</template>
