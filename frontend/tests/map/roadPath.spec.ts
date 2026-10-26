import { describe, expect, it } from 'vitest'
import { roadPath } from '@/map/roadPath'

const numbers = (d: string) => (d.match(/-?\d+(\.\d+)?/g) ?? []).map(Number)
const commands = (d: string) => (d.match(/[A-Za-z]/g) ?? []).join('')

// F11 AC-5, AC-7, ADR-76: el camino es una función pura de puntos a un trazo de SVG
describe('roadPath', () => {
  it('is empty without at least two points', () => {
    expect(roadPath([])).toBe('')
    expect(roadPath([{ x: 10, y: 10 }])).toBe('')
  })

  it('starts at the first point and ends at the last one', () => {
    const d = roadPath([{ x: 60, y: 100 }, { x: 190, y: 100 }, { x: 320, y: 100 }])

    expect(d.startsWith('M 60 100')).toBe(true)
    const n = numbers(d)
    expect(n.slice(-2)).toEqual([320, 100])
  })

  it('draws two points as a straight segment', () => {
    expect(commands(roadPath([{ x: 0, y: 0 }, { x: 100, y: 0 }]))).toBe('ML')
  })

  it('keeps straight runs straight and rounds the corners with curves (no diagonal jumps between rows)', () => {
    const d = roadPath([{ x: 60, y: 100 }, { x: 190, y: 100 }, { x: 246, y: 100 }, { x: 246, y: 160 }, { x: 60, y: 160 }, { x: 60, y: 220 }])

    expect(commands(d)).toContain('Q')
    expect(commands(d).replace(/[MLQ]/g, '')).toBe('') // solo trazos de ese vocabulario
  })

  it('passes through every point it is given when they are in a line', () => {
    const pts = [{ x: 60, y: 50 }, { x: 190, y: 50 }, { x: 320, y: 50 }, { x: 450, y: 50 }]
    const n = numbers(roadPath(pts))

    for (const p of pts) expect(n).toContain(p.x)
  })

  it('produces only finite numbers, also with repeated points', () => {
    const d = roadPath([{ x: 10, y: 10 }, { x: 10, y: 10 }, { x: 10, y: 10 }, { x: 80, y: 10 }])

    expect(numbers(d).every(Number.isFinite)).toBe(true)
    expect(d).not.toMatch(/NaN|Infinity|undefined/)
  })

  it('never leaves the bounding box of its points (the road does not escape the map)', () => {
    const pts = [{ x: 44, y: 60 }, { x: 80, y: 130 }, { x: 44, y: 200 }, { x: 80, y: 270 }]
    const n = numbers(roadPath(pts))
    const xs = n.filter((_, i) => i % 2 === 0)
    const ys = n.filter((_, i) => i % 2 === 1)

    expect(Math.min(...xs)).toBeGreaterThanOrEqual(44)
    expect(Math.max(...xs)).toBeLessThanOrEqual(80)
    expect(Math.min(...ys)).toBeGreaterThanOrEqual(60)
    expect(Math.max(...ys)).toBeLessThanOrEqual(270)
  })

  it('is deterministic and does not modify its input', () => {
    const pts = [{ x: 1, y: 2 }, { x: 50, y: 2 }, { x: 50, y: 90 }]
    const copy = JSON.stringify(pts)

    expect(roadPath(pts)).toBe(roadPath(pts))
    expect(JSON.stringify(pts)).toBe(copy)
  })

  it('handles 80 stations (a course of the maximum size) quickly and without breaking', () => {
    const pts = Array.from({ length: 80 }, (_, i) => ({ x: 60 + (i % 9) * 130, y: 60 + Math.floor(i / 9) * 120 }))

    expect(roadPath(pts).length).toBeGreaterThan(100)
  })
})
