// Las tareas de IA del backend (AiTask) dichas en palabras. Una tarea que no conocemos sale tal cual, como texto.
const LABELS: Record<string, string> = {
  OUTLINE: 'Temario',
  LESSON: 'Lecciones',
  ASSESSMENT: 'Ejercicios y exámenes',
  TUTOR: 'Tutor',
  EMBEDDING: 'Material',
  INTAKE: 'Asistente',
  IMAGE: 'Ilustraciones',
  OCR: 'Leer fotos',
}

export const taskLabel = (task: string): string => LABELS[task] ?? task
