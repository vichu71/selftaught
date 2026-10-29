/**
 * Selección automática de lo que se sube desde el asistente (F18, AC-2, ADR-99): un documento esperado se marca solo cuando está `READY`,
 * nunca por encima del máximo, nunca se desmarca nada y los que fallaron o ya no existen dejan de esperarse. Función pura: sin red ni DOM.
 */
export interface DocLite {
  id: string
  status: string
}

export function autoSelect(input: { documents: DocLite[]; selected: string[]; awaiting: string[]; max: number }): { select: string[]; awaiting: string[] } {
  const select: string[] = []
  const awaiting: string[] = []
  let count = input.selected.length
  for (const id of input.awaiting) {
    const doc = input.documents.find((d) => d.id === id)
    if (!doc || doc.status === 'ERROR') continue
    if (doc.status !== 'READY') {
      awaiting.push(id)
      continue
    }
    if (input.selected.includes(id)) continue
    if (count < input.max) {
      select.push(id)
      count++
    }
  }
  return { select, awaiting }
}
