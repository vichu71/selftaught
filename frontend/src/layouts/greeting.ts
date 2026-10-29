/** Saludo de la barra y del Inicio (F17, AC-4, AC-10): texto plano; el escape lo hace la plantilla. */

const MAX_NAME = 20

/** Primera palabra del nombre, recortada con «…» si es muy larga; vacía si no hay nombre. */
export function firstName(displayName: string | null | undefined, max = MAX_NAME): string {
  const word = (displayName ?? '').trim().split(/\s+/)[0] ?? ''
  return word.length > max ? `${word.slice(0, max - 1)}…` : word
}

/** Franja del día según el reloj del navegador: no hay petición ni hora del servidor. */
export function daypart(now: Date = new Date()): string {
  const hour = now.getHours()
  if (hour >= 6 && hour < 13) return 'Buenos días'
  if (hour >= 13 && hour < 21) return 'Buenas tardes'
  return 'Buenas noches'
}
