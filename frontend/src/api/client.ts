/**
 * Cliente de la API. La sesión viaja en una cookie HttpOnly que el navegador envía solo (mismo origen);
 * en las peticiones que modifican datos se reenvía el token CSRF en la cabecera X-XSRF-TOKEN.
 * Las credenciales van siempre en el cuerpo, nunca en la URL.
 */
export interface User {
  id: string
  displayName: string
  kind: 'ADULT' | 'MINOR'
  roles: string[]
  email?: string
  loginId?: string
}

export interface Student {
  id: string
  displayName: string
  loginId: string
  status: string
}

export interface NewStudent {
  displayName: string
  loginHint: string
  password: string
}

export type MaterialStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'ERROR'

export interface MaterialDocument {
  id: string
  name: string
  mediaType: 'PDF' | 'DOCX' | 'TEXT'
  sizeBytes: number
  status: MaterialStatus
  errorKind?: string
  chunkCount: number
  pageCount?: number
  createdAt: string
  ownedByMe: boolean
}

export interface MinorMaterial {
  student: { id: string; displayName: string }
  documents: MaterialDocument[]
}

export interface IntakeOption {
  id: string
  label: string
  isDefault: boolean
}

export interface IntakeQuestion {
  id: string
  text: string
  options: IntakeOption[]
}

export interface IntakeResult {
  intakeId: string
  questions: IntakeQuestion[]
}

export interface CourseSheet {
  schemaVersion: number
  topic: string
  focus: string
  startLevel: string
  goal: string
  topicsCount: number
  examPerTopic: boolean
  documentIds: string[]
}

export type CourseStatus = 'PENDING_GENERATION' | 'GENERATING' | 'PAUSED' | 'READY' | 'FAILED'

export interface Course {
  id: string
  title: string
  status: CourseStatus
  sheet: CourseSheet
  documents: { id: string; name: string }[]
  createdAt: string
}

export type PieceStatus = 'PENDING' | 'DONE' | 'FAILED'
export type PieceKind = 'LESSON' | 'EXERCISES' | 'EXAM'
export type StopReason = 'BUDGET' | 'PROVIDER' | 'CONTENT' | 'TOPIC_NOT_ALLOWED' | 'LIMIT'

export interface PieceRef {
  id: string
  status: PieceStatus
}

export interface OutlineChapter {
  id: string
  title: string
  objective: string
  lesson?: PieceRef | null
  exercises?: PieceRef | null
}

export interface OutlineTopic {
  id: string
  title: string
  summary: string
  chapters: OutlineChapter[]
  exam?: PieceRef | null
}

/** El curso con su temario y progreso (F05). Un curso que aún espera en la cola llega sin temario. */
export interface CourseDetail extends Course {
  stopReason?: StopReason | null
  progress?: { done: number; total: number }
  costUsd?: number
  generatedByAi?: boolean
  outline?: OutlineTopic[]
  waitingForOther?: boolean
}

/** Un ejercicio antes de responder: la solución llega al enviar la respuesta (F06, ADR-50). */
export interface Exercise {
  difficulty: 'LOW' | 'MEDIUM' | 'HIGH'
  statement: string
}

/** Una pregunta antes de entregar: la opción correcta llega con el resultado (F06, ADR-50). */
export interface ExamQuestion {
  text: string
  options: string[]
}

/** Una pieza del curso. El contenido solo llega si está hecha (`DONE`). */
export interface Piece {
  id: string
  kind: PieceKind
  status: PieceStatus
  title: string
  generatedByAi: boolean
  markdown?: string
  exercises?: Exercise[]
  questions?: ExamQuestion[]
  sources: { n: number; page?: number | null }[]
  done?: boolean
  navigation?: { previous?: string; next?: string }
}

export interface MaterialList {
  own: MaterialDocument[]
  minors: MinorMaterial[]
}

export type TutorRole = 'USER' | 'TUTOR'
/** OK es un mensaje normal; BLOCKED, una pregunta que el filtro de menores no deja enviar; FILTERED, una respuesta sustituida por un texto fijo. */
export type TutorMessageStatus = 'OK' | 'BLOCKED' | 'FILTERED'

export interface TutorMessage {
  id: string
  role: TutorRole
  status: TutorMessageStatus
  content: string
  sources?: { n: number; page?: number | null }[]
  createdAt: string
}

export interface TutorThread {
  canClear: boolean
  messages: TutorMessage[]
}

export interface TutorExchange {
  user: TutorMessage
  tutor: TutorMessage
}

// ---------- panel del adulto tutor (F08): todo de solo lectura ----------

export interface GuardianCourseSummary {
  id: string
  title: string
  status: CourseStatus
  percent?: number
  lessonsDone: number
  lessonsTotal: number
  examsPassed: number
  examsTotal: number
  createdAt: string
}

export interface GuardianUsage {
  month: string
  spendUsd: number
  limitUsd: number
  reached: boolean
  byTask: { task: string; spendUsd: number; calls: number }[]
}

export interface GuardianOverview {
  student: { id: string; displayName: string; active: boolean }
  lastActivityAt?: string
  alerts: { last30Days: number }
  usage: GuardianUsage
  courses: GuardianCourseSummary[]
}

export interface GuardianCourse {
  id: string
  title: string
  status: CourseStatus
  overview: StudyOverview
}

export interface GuardianExerciseAttempt {
  answer: string
  selfResult?: ExerciseResult
  aiResult?: ExerciseResult
  aiComment?: string
  createdAt: string
}

export interface GuardianPieceAttempts {
  pieceId: string
  kind: 'EXERCISES' | 'EXAM'
  title: string
  exercises?: { item: number; statement: string; attempts: GuardianExerciseAttempt[] }[]
  exams?: { submittedAt: string; correct: number; total: number; percent: number; passed: boolean; failed: string[] }[]
}

export interface GuardianAttempts {
  truncated: boolean
  pieces: GuardianPieceAttempts[]
}

export interface GuardianConversation {
  courseId: string
  courseTitle?: string
  pieceId: string
  lessonTitle?: string
  messages: number
  blocked: number
  filtered: number
  lastAt: string
}

export type GuardianAlertType = 'TUTOR_QUESTION_BLOCKED' | 'TUTOR_ANSWER_FILTERED' | 'COURSE_TOPIC_BLOCKED' | 'STUDY_ANSWER_BLOCKED' | 'COURSE_CONTENT_BLOCKED'

export interface GuardianAlert {
  type: GuardianAlertType
  at: string
  courseId?: string
  courseTitle?: string
  pieceId?: string
  lessonTitle?: string
  topic?: string
  category?: string
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(`HTTP ${status}${code ? ` ${code}` : ''}`)
    this.name = 'ApiError'
  }
}

function csrfToken(): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return entry ? decodeURIComponent(entry.slice('XSRF-TOKEN='.length)) : undefined
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData
  // Con FormData el navegador pone él mismo el Content-Type (con el boundary): no se fija a mano
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const init: RequestInit = { method, headers, credentials: 'same-origin' }
  if (body !== undefined) init.body = isForm ? (body as FormData) : JSON.stringify(body)
  const response = await fetch(path, init)

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiError(response.status, error?.code)
  }
  if (response.status === 204) return undefined as T
  return (await response.json().catch(() => undefined)) as T
}

const studentPath = (studentId: string) => `/api/tutor/students/${encodeURIComponent(studentId)}`
const piecePath = (courseId: string, pieceId: string) => `/api/courses/${encodeURIComponent(courseId)}/pieces/${encodeURIComponent(pieceId)}`

export const api = {
  register: (data: { email: string; password: string; displayName: string }) =>
    request<void>('POST', '/api/auth/register', data),
  verifyEmail: (token: string) => request<void>('POST', '/api/auth/verify-email', { token }),
  resendVerification: (email: string) => request<void>('POST', '/api/auth/resend-verification', { email }),
  login: (identifier: string, password: string) =>
    request<User>('POST', '/api/auth/login', { identifier, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  forgotPassword: (email: string) => request<void>('POST', '/api/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('POST', '/api/auth/reset-password', { token, newPassword }),
  me: () => request<User>('GET', '/api/me'),

  listStudents: () => request<Student[]>('GET', '/api/tutor/students'),
  createStudent: (data: NewStudent) => request<Student>('POST', '/api/tutor/students', data),
  resetStudentPassword: (id: string, newPassword: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/reset-password`, { newPassword }),
  deactivateStudent: (id: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/deactivate`),
  reactivateStudent: (id: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/reactivate`),

  listMaterial: () => request<MaterialList>('GET', '/api/material'),
  uploadMaterial: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<MaterialDocument>('POST', '/api/material', form)
  },
  deleteMaterial: (id: string) => request<void>('DELETE', `/api/material/${encodeURIComponent(id)}`),

  startIntake: (topic: string, documentIds: string[]) => request<IntakeResult>('POST', '/api/courses/intake', { topic, documentIds }),
  createCourse: (intakeId: string, answers: { questionId: string; optionId: string }[]) =>
    request<Course>('POST', '/api/courses', { intakeId, answers }),
  listCourses: () => request<Course[]>('GET', '/api/courses'),
  getCourse: (id: string) => request<CourseDetail>('GET', `/api/courses/${encodeURIComponent(id)}`),
  getPiece: (courseId: string, pieceId: string) =>
    request<Piece>('GET', `/api/courses/${encodeURIComponent(courseId)}/pieces/${encodeURIComponent(pieceId)}`),
  resumeCourse: (id: string) => request<CourseDetail>('POST', `/api/courses/${encodeURIComponent(id)}/resume`),
  exportUrl: (id: string) => exportPath(id),
  getStudyProgress: () => request<CourseProgress[]>('GET', '/api/study/progress'),
  getStudy: (courseId: string) => request<StudyOverview>('GET', `/api/courses/${encodeURIComponent(courseId)}/study`),
  setLessonDone: (courseId: string, pieceId: string, done: boolean) => request<void>('PUT', `${piecePath(courseId, pieceId)}/done`, { done }),
  sendExerciseAnswer: (courseId: string, pieceId: string, item: number, answer: string) =>
    request<ExerciseAnswered>('POST', `${piecePath(courseId, pieceId)}/exercises/${item}/attempts`, { answer }),
  setExerciseResult: (courseId: string, pieceId: string, item: number, attemptId: string, result: ExerciseResult) =>
    request<void>('PUT', `${piecePath(courseId, pieceId)}/exercises/${item}/attempts/${encodeURIComponent(attemptId)}/result`, { result }),
  aiGrade: (courseId: string, pieceId: string, item: number, attemptId: string) =>
    request<AiGrade>('POST', `${piecePath(courseId, pieceId)}/exercises/${item}/attempts/${encodeURIComponent(attemptId)}/ai-grade`),
  startExam: (courseId: string, pieceId: string) => request<StartedExam>('POST', `${piecePath(courseId, pieceId)}/exam/attempts`),
  submitExam: (courseId: string, pieceId: string, attemptId: string, answers: (number | null)[]) =>
    request<ExamResult>('POST', `${piecePath(courseId, pieceId)}/exam/attempts/${encodeURIComponent(attemptId)}/submit`, { answers }),
  getExamAttempt: (courseId: string, pieceId: string, attemptId: string) =>
    request<ExamResult>('GET', `${piecePath(courseId, pieceId)}/exam/attempts/${encodeURIComponent(attemptId)}`),
  getAttempts: (courseId: string, pieceId: string) => request<Attempts>('GET', `${piecePath(courseId, pieceId)}/attempts`),
  getGuardianOverview: (studentId: string) => request<GuardianOverview>('GET', `${studentPath(studentId)}/overview`),
  getGuardianCourse: (studentId: string, courseId: string) => request<GuardianCourse>('GET', `${studentPath(studentId)}/courses/${encodeURIComponent(courseId)}`),
  getGuardianAttempts: (studentId: string, courseId: string) =>
    request<GuardianAttempts>('GET', `${studentPath(studentId)}/courses/${encodeURIComponent(courseId)}/attempts`),
  getGuardianConversations: (studentId: string) => request<GuardianConversation[]>('GET', `${studentPath(studentId)}/conversations`),
  getGuardianThread: (studentId: string, courseId: string, pieceId: string) =>
    request<{ messages: TutorMessage[] }>('GET', `${studentPath(studentId)}/courses/${encodeURIComponent(courseId)}/pieces/${encodeURIComponent(pieceId)}/tutor`),
  getGuardianAlerts: (studentId: string) => request<GuardianAlert[]>('GET', `${studentPath(studentId)}/alerts`),
  getTutorThread: (courseId: string, pieceId: string) => request<TutorThread>('GET', `${piecePath(courseId, pieceId)}/tutor`),
  askTutor: (courseId: string, pieceId: string, question: string) => request<TutorExchange>('POST', `${piecePath(courseId, pieceId)}/tutor`, { question }),
  clearTutor: (courseId: string, pieceId: string) => request<void>('DELETE', `${piecePath(courseId, pieceId)}/tutor`),
  getResults: () => request<Results>('GET', '/api/results'),
  getUsage: () => request<Usage>('GET', '/api/ai/usage'),
}

/** Dirección de la exportación del curso (se abre como descarga; no es una llamada de la API). */
export function exportPath(id: string): string {
  return `/api/courses/${encodeURIComponent(id)}/export`
}

export interface CourseResult {
  id: string
  title: string
  status: CourseStatus
  stopReason?: StopReason | null
  done: number
  failed: number
  pending: number
  total: number
  calls: number
  costUsd?: number
  createdAt: string
}

/** Resultados de los cursos propios; un menor recibe las piezas pero ningún importe. */
export interface Results {
  courses: CourseResult[]
  totalCostUsd?: number
}

export interface Usage {
  month: string
  you: { calls: number; spendUsd?: number; limitUsd?: number; limitReached: boolean }
  budget?: { percent: number; level: string }
}

// ---------- Estudio (F06) ----------

export type ExerciseResult = 'CORRECT' | 'PARTIAL' | 'INCORRECT'

export interface ExamState {
  pieceId: string
  status: PieceStatus
  attempts: number
  best?: number
  last?: number
  passed: boolean
}

export interface TopicStudy {
  id: string
  percent: number
  lessons: { pieceId: string; status: PieceStatus; done: boolean }[]
  exam?: ExamState
}

/** El avance del curso: lecciones hechas y exámenes superados sobre todo el temario. Sin temario no hay porcentaje. */
export interface StudyOverview {
  percent?: number
  lessonsDone: number
  lessonsTotal: number
  examsPassed: number
  examsTotal: number
  topics: TopicStudy[]
}

export interface CourseProgress {
  courseId: string
  percent?: number
}

export interface ExerciseAnswered {
  attemptId: string
  solution: string
  explanation: string
}

export interface AiGrade {
  verdict: ExerciseResult
  comment: string
}

export interface StartedExam {
  attemptId: string
  questions: { text: string; options: string[] }[]
}

export interface ExamResult {
  correct: number
  total: number
  percent: number
  passed: boolean
  questions: { text: string; options: string[]; chosen?: number | null; correct: number; explanation: string; ok: boolean }[]
}

export interface ExerciseAttempt {
  id: string
  itemIndex: number
  answer: string
  selfResult?: ExerciseResult
  aiResult?: ExerciseResult
  aiComment?: string
  createdAt: string
}

export interface ExamAttemptSummary {
  id: string
  correct: number
  total: number
  percent: number
  passed: boolean
  submittedAt: string
}

export type Attempts =
  | { kind: 'EXERCISES'; attempts: ExerciseAttempt[] }
  | { kind: 'EXAM'; attempts: ExamAttemptSummary[] }
