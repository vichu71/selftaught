import { describe, expect, it } from 'vitest'
import { STAGE_LABEL, plantStage } from '@/world/plantStage'

// F11 AC-3, ADR-74: la fase de la planta es una función pura del porcentaje
describe('plantStage', () => {
  it.each([
    [0, 'seed'],
    [1, 'sprout'],
    [20, 'sprout'],
    [39, 'sprout'],
    [40, 'tree'],
    [75, 'tree'],
    [99, 'tree'],
    [100, 'fruit'],
  ])('%i %% is a %s', (percent, stage) => {
    expect(plantStage(percent)).toBe(stage)
  })

  it('without a percentage (course waiting or being generated) it is a seed', () => {
    expect(plantStage(undefined)).toBe('seed')
    expect(plantStage(null)).toBe('seed')
  })

  it('is total: values out of range or not numbers are bounded', () => {
    expect(plantStage(-5)).toBe('seed')
    expect(plantStage(250)).toBe('fruit')
    expect(plantStage(Number.NaN)).toBe('seed')
    expect(plantStage(Number.POSITIVE_INFINITY)).toBe('fruit')
  })

  it('rounds half percentages like the server does (39.6 is 40)', () => {
    expect(plantStage(39.4)).toBe('sprout')
    expect(plantStage(39.6)).toBe('tree')
  })

  // AC-3: la fase se dice siempre con texto
  it('has a plain-words label for each stage', () => {
    expect(STAGE_LABEL).toEqual({ seed: 'Semilla', sprout: 'Brote', tree: 'Árbol', fruit: 'Árbol con frutos' })
  })
})
