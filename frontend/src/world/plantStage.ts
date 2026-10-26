/**
 * Fase de la planta de un curso (F11, ADR-74): función pura del porcentaje de avance que ya calcula el servidor.
 * 0 % semilla · 1–39 % brote · 40–99 % árbol · 100 % árbol con frutos. Sin porcentaje (en cola o generándose) es una semilla.
 */
export type PlantStage = 'seed' | 'sprout' | 'tree' | 'fruit'

export const STAGE_LABEL: Record<PlantStage, string> = {
  seed: 'Semilla',
  sprout: 'Brote',
  tree: 'Árbol',
  fruit: 'Árbol con frutos',
}

export function plantStage(percent?: number | null): PlantStage {
  if (percent === undefined || percent === null || Number.isNaN(percent)) return 'seed'
  const p = Math.round(percent)
  if (p >= 100) return 'fruit'
  if (p >= 40) return 'tree'
  if (p >= 1) return 'sprout'
  return 'seed'
}
