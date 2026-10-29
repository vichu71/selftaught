import { describe, expect, it } from 'vitest'
import { zoneOf } from '@/layouts/zone'

// F17 ART-004: la zona de color sale solo de la ruta, con una función pura
describe('zoneOf', () => {
  it.each([
    ['/', 'map'], ['/nuevo-curso', 'map'], ['/cursos/c1', 'map'], ['/cursos/c1/piezas/p1', 'map'], ['/cursos/c1/piezas/p1/intentos', 'map'],
    ['/cursos/c1/editar', 'map'], ['/codigo', 'map'], ['/alumnos/a1', 'map'], ['/alumnos/a1/cursos/c1', 'map'],
    ['/material', 'material'], ['/material/algo', 'material'],
    ['/resultados', 'results'],
    ['/propietario', 'owner'],
    ['/login', 'neutral'], ['/register', 'neutral'], ['/forgot-password', 'neutral'], ['/reset-password', 'neutral'], ['/verify-email', 'neutral'],
    ['/ruta-que-no-existe', 'neutral'], ['', 'neutral'],
  ])('maps %s to %s', (path, zone) => {
    expect(zoneOf(path)).toBe(zone)
  })

  it('does not confuse a path that merely starts with the same letters', () => {
    expect(zoneOf('/materialista')).toBe('neutral')
    expect(zoneOf('/resultadosx')).toBe('neutral')
  })
})
