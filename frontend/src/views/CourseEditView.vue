<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError, api, type CourseDetail, type OutlineChapter, type OutlineTopic, type PieceRef } from '@/api/client'
import Breadcrumb from '@/layouts/Breadcrumb.vue'
import LessonEditor from '@/components/LessonEditor.vue'
import ShareCodes from '@/components/ShareCodes.vue'
import UiDialog from '@/ui/UiDialog.vue'
import UiTabs from '@/ui/UiTabs.vue'
import { BLOCKED_TEXT } from '@/stores/course'

const route = useRoute()
const courseId = computed(() => String(route.params.id))

const course = ref<CourseDetail | null>(null)
const notFound = ref(false)
const error = ref('')
const working = ref(false)
const lessonOpen = ref('')

type Kind = 'rename-topic' | 'rename-chapter' | 'add-chapter' | 'add-topic'
const form = reactive({ kind: '' as Kind | '', id: '', title: '', summary: '', objective: '', chapterTitle: '' })
const confirming = reactive<{ message: string; run: (() => Promise<CourseDetail>) | null }>({ message: '', run: null })

const tab = ref('outline')
const tabs = [{ id: 'outline', label: 'Temario' }, { id: 'share', label: 'Compartir' }]
const confirmOpen = computed(() => confirming.message !== '')

const outline = computed<OutlineTopic[]>(() => course.value?.outline ?? [])
const busy = computed(() => course.value?.status === 'PENDING_GENERATION' || course.value?.status === 'GENERATING')
const locked = computed(() => busy.value || working.value)

const errorMessages: Record<string, string> = {
  COURSE_BUSY: 'El curso se está generando. Espera a que termine para editarlo.',
  EDIT_RATE_LIMITED: 'Has pedido muchas cosas a la IA seguidas. Espera un rato e inténtalo de nuevo.',
  COURSE_TOO_BIG: 'El curso tendría demasiadas partes. Quita alguna antes de añadir más.',
  EDIT_NOT_ALLOWED: BLOCKED_TEXT,
}

async function load() {
  try {
    course.value = await api.getCourse(courseId.value)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else error.value = 'No se pudo cargar el curso. Inténtalo más tarde.'
  }
}

// Mientras la IA prepara lo nuevo se vuelve a mirar el curso cada pocos segundos hasta que acabe
let timer: ReturnType<typeof setInterval> | undefined
watch(busy, (isBusy) => {
  if (timer !== undefined) clearInterval(timer)
  timer = isBusy ? setInterval(() => void load(), 4000) : undefined
})
onMounted(load)
onBeforeUnmount(() => timer !== undefined && clearInterval(timer))

/** Hace un cambio, enseña el curso que devuelve el servidor y, si falla, lo dice con palabras sencillas sin enseñar el código. */
async function run(change: () => Promise<CourseDetail>, closeForm = true) {
  if (working.value) return
  working.value = true
  error.value = ''
  try {
    course.value = await change()
    if (closeForm) closeForms()
  } catch (e) {
    error.value = (e instanceof ApiError && e.code && errorMessages[e.code]) || 'No se pudo guardar el cambio. Inténtalo de nuevo.'
    confirming.message = ''
    confirming.run = null
  } finally {
    working.value = false
  }
}

function closeForms() {
  form.kind = ''
  confirming.message = ''
  confirming.run = null
}

// ---------- reordenar ----------

function move<T extends { id: string }>(list: T[], id: string, delta: number): string[] {
  const ids = list.map((item) => item.id)
  const from = ids.indexOf(id)
  const to = from + delta
  if (from < 0 || to < 0 || to >= ids.length) return ids
  ;[ids[from], ids[to]] = [ids[to], ids[from]]
  return ids
}

const moveTopic = (id: string, delta: number) => run(() => api.reorderTopics(courseId.value, move(outline.value, id, delta)), false)
const moveChapter = (topic: OutlineTopic, id: string, delta: number) => run(() => api.reorderChapters(courseId.value, topic.id, move(topic.chapters, id, delta)), false)

// ---------- formularios ----------

function open(kind: Kind, id = '', topic?: OutlineTopic, chapter?: OutlineChapter) {
  error.value = ''
  confirming.message = ''
  form.kind = kind
  form.id = id
  form.title = kind === 'rename-topic' ? (topic?.title ?? '') : kind === 'rename-chapter' ? (chapter?.title ?? '') : ''
  form.summary = kind === 'rename-topic' ? (topic?.summary ?? '') : ''
  form.objective = kind === 'rename-chapter' ? (chapter?.objective ?? '') : ''
  form.chapterTitle = ''
}

function valid(value: string, max: number): boolean {
  const text = value.trim()
  return text.length > 0 && text.length <= max
}

function save() {
  const title = form.title.trim()
  const summary = form.summary.trim()
  const objective = form.objective.trim()
  const chapterTitle = form.chapterTitle.trim()
  const ok = {
    'rename-topic': valid(title, 120) && valid(summary, 300),
    'rename-chapter': valid(title, 120) && valid(objective, 200),
    'add-chapter': valid(title, 120) && valid(objective, 200),
    'add-topic': valid(title, 120) && valid(summary, 300) && valid(chapterTitle, 120) && valid(objective, 200),
  }[form.kind as Kind]
  if (!ok) {
    error.value = 'Rellena todos los campos (el título hasta 120 caracteres, el resumen hasta 300 y el objetivo hasta 200).'
    return
  }
  const id = form.id
  if (form.kind === 'rename-topic') void run(() => api.renameTopic(courseId.value, id, { title, summary }))
  else if (form.kind === 'rename-chapter') void run(() => api.renameChapter(courseId.value, id, { title, objective }))
  else if (form.kind === 'add-chapter') void run(() => api.addChapter(courseId.value, id, { title, objective }))
  else void run(() => api.addTopic(courseId.value, { title, summary, chapter: { title: chapterTitle, objective } }))
}

// ---------- confirmaciones ----------

function ask(message: string, change: () => Promise<CourseDetail>) {
  error.value = ''
  form.kind = ''
  confirming.message = message
  confirming.run = change
}

const REMOVE = 'Se borrarán sus lecciones, ejercicios y examen, y también tu progreso, tus intentos y tus conversaciones con el tutor de esas partes. No se puede deshacer.'
const REGENERATE = 'Se volverá a generar con IA (cuenta para tu límite y tu gasto) y se borrarán los intentos, el progreso y las conversaciones que dependían de esta parte.'

const removeTopic = (id: string) => ask(REMOVE, () => api.removeTopic(courseId.value, id))
const removeChapter = (id: string) => ask(REMOVE, () => api.removeChapter(courseId.value, id))
const regenerate = (id: string) => ask(REGENERATE, () => api.regeneratePiece(courseId.value, id))

function confirm() {
  const change = confirming.run
  if (change) void run(change)
}

function lessonSaved(updated: CourseDetail) {
  course.value = updated
  lessonOpen.value = ''
}

const pieces = (chapter: OutlineChapter): { label: string; piece: PieceRef }[] =>
  [chapter.lesson ? { label: 'Lección', piece: chapter.lesson } : null, chapter.exercises ? { label: 'Ejercicios', piece: chapter.exercises } : null].filter((p) => p !== null) as { label: string; piece: PieceRef }[]
</script>

<template>
  <div class="page wide" data-testid="course-edit">
    <Breadcrumb :items="[{ label: 'Mapa', to: '/' }, { label: course?.title ?? 'Curso', to: `/cursos/${courseId}` }, { label: 'Editar' }]" />
    <header class="topbar">
      <h1 v-if="course">Editar «{{ course.title }}»</h1>
      <h1 v-else>Editar curso</h1>
      <RouterLink :to="`/cursos/${courseId}`" data-testid="back-course">Volver al curso</RouterLink>
    </header>

    <p v-if="notFound" class="error" role="alert" data-testid="edit-not-found">No encontramos este curso.</p>

    <template v-if="course">
      <p class="muted" data-testid="edit-version">Versión {{ course.version ?? 1 }}</p>
      <p v-if="busy" class="notice" role="status" data-testid="edit-busy">El curso se está preparando con la IA. Podrás editarlo cuando termine.</p>
      <p v-if="error" class="error" role="alert" data-testid="edit-error">{{ error }}</p>

      <UiDialog
        :model-value="confirmOpen"
        title="¿Seguro?"
        :description="confirming.message"
        confirm-label="Sí, continuar"
        cancel-label="No"
        tone="danger"
        :busy="working"
        confirm-testid="edit-confirm-yes"
        cancel-testid="edit-confirm-no"
        data-testid="edit-confirm"
        @update:model-value="(open) => !open && closeForms()"
        @confirm="confirm"
      />

      <UiTabs v-model="tab" :tabs="tabs" id-prefix="edit-tab" label="Edición del curso">
      <template #outline>
      <section v-if="form.kind" class="card" data-testid="edit-form">
        <h2>{{ form.kind === 'add-topic' ? 'Añadir un tema' : form.kind === 'add-chapter' ? 'Añadir un capítulo' : 'Renombrar' }}</h2>
        <label for="edit-title">Título</label>
        <input id="edit-title" v-model="form.title" type="text" maxlength="120" data-testid="edit-title" />
        <template v-if="form.kind === 'rename-topic' || form.kind === 'add-topic'">
          <label for="edit-summary">Resumen</label>
          <input id="edit-summary" v-model="form.summary" type="text" maxlength="300" data-testid="edit-summary" />
        </template>
        <template v-if="form.kind === 'add-topic'">
          <label for="edit-chapter-title">Título de su primer capítulo</label>
          <input id="edit-chapter-title" v-model="form.chapterTitle" type="text" maxlength="120" data-testid="edit-chapter-title" />
        </template>
        <template v-if="form.kind !== 'rename-topic'">
          <label for="edit-objective">Objetivo{{ form.kind === 'add-topic' ? ' de ese capítulo' : '' }}</label>
          <input id="edit-objective" v-model="form.objective" type="text" maxlength="200" data-testid="edit-objective" />
        </template>
        <p v-if="form.kind === 'add-topic' || form.kind === 'add-chapter'" class="muted" data-testid="edit-ai-notice">
          Lo nuevo lo escribe la IA al guardar: cuenta para tu límite de 20 peticiones por hora y para tu gasto del mes.
        </p>
        <p class="actions">
          <button type="button" class="primary" data-testid="edit-save" :disabled="working" @click="save">Guardar</button>
          <button type="button" data-testid="edit-cancel" @click="closeForms">Cancelar</button>
        </p>
      </section>

      <section class="card">
        <h2>Temario</h2>
        <ol class="guardian-list">
          <li v-for="(topic, ti) in outline" :key="topic.id" :data-testid="`edit-topic-${topic.id}`">
            <p>
              <strong>{{ topic.title }}</strong> <span class="muted">{{ topic.summary }}</span>
            </p>
            <p class="actions">
              <button type="button" :data-testid="`edit-topic-up-${topic.id}`" :disabled="locked || ti === 0" @click="moveTopic(topic.id, -1)">Subir</button>
              <button type="button" :data-testid="`edit-topic-down-${topic.id}`" :disabled="locked || ti === outline.length - 1" @click="moveTopic(topic.id, 1)">Bajar</button>
              <button type="button" :data-testid="`edit-topic-rename-${topic.id}`" :disabled="locked" @click="open('rename-topic', topic.id, topic)">Renombrar</button>
              <button type="button" :data-testid="`edit-topic-remove-${topic.id}`" :disabled="locked || outline.length === 1" @click="removeTopic(topic.id)">Quitar</button>
              <button type="button" :data-testid="`edit-chapter-add-${topic.id}`" :disabled="locked" @click="open('add-chapter', topic.id)">Añadir capítulo</button>
            </p>
            <p v-if="topic.exam" class="muted">
              Examen del tema
              <span v-if="topic.exam.outdated" class="error" :data-testid="`edit-outdated-${topic.exam.id}`">· El temario del tema cambió: puede estar desactualizado</span>
              <button v-if="topic.exam.status === 'DONE'" type="button" :data-testid="`edit-regenerate-${topic.exam.id}`" :disabled="locked" @click="regenerate(topic.exam.id)">Regenerar con IA</button>
            </p>

            <ul class="guardian-list">
              <li v-for="(chapter, ci) in topic.chapters" :key="chapter.id" :data-testid="`edit-chapter-${chapter.id}`">
                <p>{{ chapter.title }} <span class="muted">{{ chapter.objective }}</span></p>
                <p class="actions">
                  <button type="button" :data-testid="`edit-chapter-up-${chapter.id}`" :disabled="locked || ci === 0" @click="moveChapter(topic, chapter.id, -1)">Subir</button>
                  <button type="button" :data-testid="`edit-chapter-down-${chapter.id}`" :disabled="locked || ci === topic.chapters.length - 1" @click="moveChapter(topic, chapter.id, 1)">Bajar</button>
                  <button type="button" :data-testid="`edit-chapter-rename-${chapter.id}`" :disabled="locked" @click="open('rename-chapter', chapter.id, topic, chapter)">Renombrar</button>
                  <button type="button" :data-testid="`edit-chapter-remove-${chapter.id}`" :disabled="locked || topic.chapters.length === 1" @click="removeChapter(chapter.id)">Quitar</button>
                  <button v-if="chapter.lesson?.status === 'DONE'" type="button" :data-testid="`edit-lesson-${chapter.lesson.id}`" :disabled="locked" @click="lessonOpen = chapter.lesson.id">Editar lección</button>
                </p>
                <p v-for="p in pieces(chapter)" :key="p.piece.id" class="muted">
                  {{ p.label }}
                  <span v-if="p.piece.outdated" class="error" :data-testid="`edit-outdated-${p.piece.id}`">· La lección cambió: puede estar desactualizado</span>
                  <button v-if="p.piece.status === 'DONE'" type="button" :data-testid="`edit-regenerate-${p.piece.id}`" :disabled="locked" @click="regenerate(p.piece.id)">Regenerar con IA</button>
                </p>
                <LessonEditor v-if="lessonOpen && chapter.lesson?.id === lessonOpen" :course-id="courseId" :piece-id="lessonOpen" @saved="lessonSaved" @cancel="lessonOpen = ''" />
              </li>
            </ul>
          </li>
        </ol>
        <p><button type="button" data-testid="edit-topic-add" :disabled="locked" @click="open('add-topic')">Añadir tema</button></p>
      </section>

      </template>
      <template #share>
        <ShareCodes :course-id="courseId" :status="course.status" />
      </template>
      </UiTabs>
    </template>
    <p v-else-if="error" class="error" role="alert" data-testid="edit-error">{{ error }}</p>
  </div>
</template>
