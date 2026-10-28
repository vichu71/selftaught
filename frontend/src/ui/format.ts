// Formato de las cifras del panel del propietario (F14): siempre en español y sin dejar escapar un NaN.

const locale = 'es-ES'

export function usd(value: number | null | undefined): string {
  return (Number.isFinite(value) ? (value as number) : 0).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function num(value: number | null | undefined, decimals = 0): string {
  return (Number.isFinite(value) ? (value as number) : 0).toLocaleString(locale, { minimumFractionDigits: 0, maximumFractionDigits: decimals })
}

/** «1 vez» y «N veces». */
export function times(value: number | null | undefined): string {
  const n = Number.isFinite(value) ? (value as number) : 0
  return n === 1 ? '1 vez' : `${num(n)} veces`
}

/** Una cifra con su unidad: «USD» siempre con dos decimales, «veces» en singular o plural, el resto con uno como mucho. */
export function withUnit(value: number, unit?: string): string {
  if (unit === 'USD') return usd(value)
  if (unit === 'veces') return times(value)
  return unit ? `${num(value, 1)} ${unit}` : num(value, 1)
}

export function percent(value: number | null | undefined): string {
  return `${num(value, 1)} %`
}

export function millis(value: number | null | undefined): string {
  return `${num(value)} ms`
}

/** «2026-10-25» → «25-10-2026»; sin fecha, «sin fecha». */
export function dateOf(iso: string | null | undefined): string {
  if (!iso) return 'sin fecha'
  const [year, month, day] = iso.slice(0, 10).split('-')
  return year && month && day ? `${day}-${month}-${year}` : 'sin fecha'
}

export function dateTimeOf(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(locale, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function megabytes(bytes: number | null | undefined): string {
  return `${num((Number.isFinite(bytes) ? (bytes as number) : 0) / 1048576, 1)} MB`
}
