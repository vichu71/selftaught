import { describe, expect, it } from 'vitest'
import { autoSelect } from '@/material/autoSelect'

const doc = (id: string, status: 'UPLOADED' | 'PROCESSING' | 'READY' | 'ERROR') => ({ id, status })

// F18 AC-2: un documento subido desde el asistente se marca solo al estar listo, sin pasar del máximo
describe('autoSelect', () => {
  it('selects an awaited document once it is READY and stops awaiting it', () => {
    const out = autoSelect({ documents: [doc('a', 'READY')], selected: [], awaiting: ['a'], max: 5 })

    expect(out.select).toEqual(['a'])
    expect(out.awaiting).toEqual([])
  })

  it('keeps awaiting the ones still being processed and selects nothing for them', () => {
    const out = autoSelect({ documents: [doc('a', 'UPLOADED'), doc('b', 'PROCESSING')], selected: [], awaiting: ['a', 'b'], max: 5 })

    expect(out.select).toEqual([])
    expect(out.awaiting).toEqual(['a', 'b'])
  })

  it('drops the ones that failed or disappeared, without selecting them', () => {
    const out = autoSelect({ documents: [doc('a', 'ERROR')], selected: [], awaiting: ['a', 'gone'], max: 5 })

    expect(out.select).toEqual([])
    expect(out.awaiting).toEqual([])
  })

  it('never goes over the maximum and drops the extra ones from the waiting list', () => {
    const out = autoSelect({ documents: [doc('c', 'READY'), doc('d', 'READY')], selected: ['a', 'b', 'x'], awaiting: ['c', 'd'], max: 4 })

    expect(out.select).toEqual(['c'])
    expect(out.awaiting).toEqual([])
  })

  it('does not select twice one that is already selected and never deselects', () => {
    const out = autoSelect({ documents: [doc('a', 'READY')], selected: ['a'], awaiting: ['a'], max: 5 })

    expect(out.select).toEqual([])
    expect(out.awaiting).toEqual([])
  })

  it('does nothing when nothing is awaited', () => {
    const out = autoSelect({ documents: [doc('a', 'READY')], selected: [], awaiting: [], max: 5 })

    expect(out).toEqual({ select: [], awaiting: [] })
  })
})
