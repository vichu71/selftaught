export interface Point { x: number; y: number }

const round = (v: number) => Math.round(v * 100) / 100

/**
 * Camino por las paradas (F11, ADR-76): de una lista de puntos a un trazo de SVG con las esquinas redondeadas (curvas `Q`).
 * Función pura, sin DOM: los tramos rectos van con `L` y cada esquina se suaviza con un radio que nunca pasa de la mitad de un lado.
 */
export function roadPath(points: Point[], radius = 26): string {
  const pts: Point[] = []
  for (const p of points) {
    const last = pts[pts.length - 1]
    if (!last || Math.hypot(p.x - last.x, p.y - last.y) > 0.001) pts.push(p)
  }
  if (pts.length < 2) return ''

  const out = [`M ${round(pts[0].x)} ${round(pts[0].y)}`]
  for (let i = 1; i < pts.length - 1; i++) {
    const prev = pts[i - 1]
    const cur = pts[i]
    const next = pts[i + 1]
    const lenIn = Math.hypot(cur.x - prev.x, cur.y - prev.y)
    const lenOut = Math.hypot(next.x - cur.x, next.y - cur.y)
    const cross = (cur.x - prev.x) * (next.y - cur.y) - (cur.y - prev.y) * (next.x - cur.x)
    if (Math.abs(cross) < 0.001) {
      out.push(`L ${round(cur.x)} ${round(cur.y)}`)
      continue
    }
    const r = Math.min(radius, lenIn / 2, lenOut / 2)
    const start = { x: cur.x - ((cur.x - prev.x) / lenIn) * r, y: cur.y - ((cur.y - prev.y) / lenIn) * r }
    const end = { x: cur.x + ((next.x - cur.x) / lenOut) * r, y: cur.y + ((next.y - cur.y) / lenOut) * r }
    out.push(`L ${round(start.x)} ${round(start.y)}`, `Q ${round(cur.x)} ${round(cur.y)} ${round(end.x)} ${round(end.y)}`)
  }
  const last = pts[pts.length - 1]
  out.push(`L ${round(last.x)} ${round(last.y)}`)
  return out.join(' ')
}
