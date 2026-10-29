/**
 * Zona de color de una ruta (F17, ADR-95): función pura del path. La usan el marco (`AppShell`, que la pone en `data-zone`),
 * la barra (`NavBar`, que marca el enlace activo) y los tests. Login, registro y lo que no tiene zona propia son «neutral».
 */
export type Zone = 'map' | 'material' | 'results' | 'owner' | 'neutral'

const MAP_ROOTS = new Set(['', 'nuevo-curso', 'cursos', 'codigo', 'alumnos'])

export function zoneOf(path: string): Zone {
  const root = path.split(/[?#]/)[0].split('/')[1] ?? ''
  if (root === 'material') return 'material'
  if (root === 'resultados') return 'results'
  if (root === 'propietario') return 'owner'
  if (path === '' ) return 'neutral'
  return MAP_ROOTS.has(root) ? 'map' : 'neutral'
}
