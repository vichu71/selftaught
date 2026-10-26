import type { OutlineTopic, PieceRef, PieceStatus, StudyOverview } from '@/api/client'

export const piece = (id: string, status: PieceStatus = 'DONE', outdated = false): PieceRef => ({ id, status, outdated })

/**
 * Tres temas. Hecho: l1, l2 y el examen x1 (superado). Lista y sin hacer: l3 (será «ahora»).
 * En cola: l4, x2, l5, x3.
 */
export const outline = (): OutlineTopic[] => [
  {
    id: 't1', title: 'Mesopotamia', summary: 'Las primeras ciudades',
    chapters: [
      { id: 'ch1', title: 'Ciudades entre dos ríos', objective: 'Entender el origen', lesson: piece('l1'), exercises: piece('e1') },
      { id: 'ch2', title: 'La escritura', objective: 'Conocer la cuneiforme', lesson: piece('l2'), exercises: piece('e2') },
    ],
    exam: piece('x1'),
  },
  {
    id: 't2', title: 'Egipto', summary: 'El Nilo y los faraones',
    chapters: [
      { id: 'ch3', title: 'El Nilo y su gente', objective: 'Ver la crecida', lesson: piece('l3'), exercises: piece('e3') },
      { id: 'ch4', title: 'Faraones', objective: 'Templos y pirámides', lesson: piece('l4', 'PENDING'), exercises: piece('e4', 'PENDING') },
    ],
    exam: piece('x2', 'PENDING'),
  },
  {
    id: 't3', title: 'Grecia', summary: 'La polis',
    chapters: [{ id: 'ch5', title: 'La polis', objective: 'Entender la ciudad-estado', lesson: piece('l5', 'PENDING'), exercises: piece('e5', 'PENDING') }],
    exam: piece('x3', 'PENDING'),
  },
]

export const study = (): StudyOverview => ({
  percent: 38, lessonsDone: 2, lessonsTotal: 5, examsPassed: 1, examsTotal: 3,
  topics: [
    {
      id: 't1', percent: 100,
      lessons: [{ pieceId: 'l1', status: 'DONE', done: true }, { pieceId: 'l2', status: 'DONE', done: true }],
      exam: { pieceId: 'x1', status: 'DONE', attempts: 1, best: 80, last: 80, passed: true },
    },
    {
      id: 't2', percent: 0,
      lessons: [{ pieceId: 'l3', status: 'DONE', done: false }, { pieceId: 'l4', status: 'PENDING', done: false }],
      exam: { pieceId: 'x2', status: 'PENDING', attempts: 0, passed: false },
    },
    {
      id: 't3', percent: 0,
      lessons: [{ pieceId: 'l5', status: 'PENDING', done: false }],
      exam: { pieceId: 'x3', status: 'PENDING', attempts: 0, passed: false },
    },
  ],
})

/** Un curso grande: `topics` temas con `chapters` capítulos cada uno, todo listo y sin hacer. */
export function bigOutline(topics: number, chapters: number): OutlineTopic[] {
  return Array.from({ length: topics }, (_, ti) => ({
    id: `T${ti}`, title: `Tema ${ti + 1}`, summary: 'Resumen',
    chapters: Array.from({ length: chapters }, (_, ci) => ({
      id: `C${ti}-${ci}`, title: `Capítulo ${ti + 1}.${ci + 1}`, objective: 'Objetivo',
      lesson: piece(`L${ti}-${ci}`), exercises: piece(`E${ti}-${ci}`),
    })),
    exam: piece(`X${ti}`),
  }))
}
