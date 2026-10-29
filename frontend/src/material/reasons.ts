// Por qué un documento acabó con error, dicho en una frase (Mi material y el asistente de «Nuevo curso», F18).
const reasons: Record<string, string> = {
  NO_TEXT: 'No contiene texto: los documentos escaneados no se admiten.',
  ENCRYPTED: 'Está protegido con contraseña.',
  CORRUPT: 'El fichero está dañado o no se puede leer.',
  TOO_MANY_PAGES: 'Tiene demasiadas páginas.',
  TOO_LARGE: 'Es demasiado grande una vez descomprimido.',
  TOO_MUCH_TEXT: 'Contiene demasiado texto.',
  INDEXING_UNAVAILABLE: 'El servicio de indexado no está disponible ahora. Bórralo y vuelve a subirlo más tarde.',
}

export function reasonText(kind?: string): string {
  return (kind && reasons[kind]) || 'No se pudo procesar el documento.'
}
