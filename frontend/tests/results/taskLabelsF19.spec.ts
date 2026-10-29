import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { taskLabel } from '@/results/taskLabels'
import GuardianUsage from '@/components/GuardianUsage.vue'

// F19 ART-004c: la tarea OCR de la capa de IA se dice en palabras en Resultados y en el panel del tutor (una tarea desconocida saldría como código)
describe('etiqueta de la tarea OCR', () => {
  it('is said in words in Results, not as the raw code', () => {
    expect(taskLabel('OCR')).toBe('Leer fotos')
  })

  it('keeps the other labels', () => {
    expect(taskLabel('IMAGE')).toBe('Ilustraciones')
    expect(taskLabel('TUTOR')).toBe('Tutor')
  })

  it('is said in words in the guardian usage card, without the raw code', () => {
    const usage = { month: '2026-10', spendUsd: 0.5, limitUsd: 2, reached: false, byTask: [{ task: 'OCR', spendUsd: 0.1, calls: 3 }] }

    const w = mount(GuardianUsage, { props: { usage: usage as never } })

    expect(w.text().toLowerCase()).toContain('foto')
    expect(w.text()).not.toContain('OCR')
  })
})
