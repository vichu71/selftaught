// Por qué un documento acabó con error, dicho en una frase (Mi material y el asistente de «Nuevo curso», F18; fotos de apuntes, F19).
const reasons: Record<string, string> = {
  NO_TEXT: 'No contiene texto: los documentos escaneados no se admiten.',
  ENCRYPTED: 'Está protegido con contraseña.',
  CORRUPT: 'El fichero está dañado o no se puede leer.',
  TOO_MANY_PAGES: 'Tiene demasiadas páginas.',
  TOO_LARGE: 'Es demasiado grande una vez descomprimido.',
  TOO_MUCH_TEXT: 'Contiene demasiado texto.',
  INDEXING_UNAVAILABLE: 'El servicio de indexado no está disponible ahora. Bórralo y vuelve a subirlo más tarde.',
  // Fotos (F19). BLOCKED es el mensaje acordado para menores: sin categoría ni motivo
  IMAGE_INVALID: 'No se puede leer la foto. Prueba con otra en JPEG o PNG.',
  IMAGE_TOO_LARGE: 'La foto es demasiado grande. Hazla con menos resolución.',
  READ_FAILED: 'No he podido leer la foto ahora. Bórrala y vuelve a subirla más tarde.',
  BUDGET: 'Has alcanzado el tope de uso de IA de este mes, así que la foto no se ha leído.',
  BLOCKED: 'Contenido inapropiado. Consulta con tu adulto.',
}

/** Una foto sin texto no es un «escaneado»: se invita a repetirla con mejores condiciones. */
const photoReasons: Record<string, string> = {
  NO_TEXT: 'No he podido leer texto en la foto: prueba con más luz y la hoja bien enfocada.',
}

export function reasonText(kind?: string, mediaType?: string): string {
  if (kind && mediaType === 'IMAGE' && photoReasons[kind]) return photoReasons[kind]
  return (kind && reasons[kind]) || 'No se pudo procesar el documento.'
}
