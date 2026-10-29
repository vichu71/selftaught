import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api, type MaterialDocument, type MaterialUsageMap, type MinorMaterial } from '@/api/client'

const POLL_MS = 3000
const MAX_BYTES = 10 * 1024 * 1024
const ALLOWED = ['pdf', 'docx', 'txt', 'md']

const uploadMessages: Record<string, string> = {
  MATERIAL_LIMIT_REACHED: 'Has alcanzado el límite de documentos. Borra alguno para subir otro.',
  MATERIAL_DUPLICATE: 'Este documento ya está subido.',
  MATERIAL_TOO_LARGE: 'El fichero supera el máximo de 10 MB.',
  MATERIAL_TYPE_NOT_ALLOWED: 'Solo se admiten PDF, Word (.docx) y texto (.txt, .md) en UTF-8.',
  MATERIAL_EMPTY: 'El fichero está vacío.',
}

/** Lista de material, subida, borrado y sondeo del estado mientras haya documentos en curso. Nada de esto toca el DOM ni guarda datos en el navegador. */
export const useMaterialStore = defineStore('material', () => {
  const own = ref<MaterialDocument[]>([])
  const minors = ref<MinorMaterial[]>([])
  const listError = ref(false)
  const uploadError = ref('')
  const deleteError = ref(false)
  const uploading = ref(false)
  /** F18: en qué cursos se usa cada documento; `null` si no se pudo leer (la lista funciona igual, sin esa línea). */
  const usage = ref<MaterialUsageMap | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let polling = false

  const pending = computed(() =>
    [...own.value, ...minors.value.flatMap((m) => m.documents)].some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING'),
  )

  async function load() {
    try {
      const result = await api.listMaterial()
      own.value = result.own
      minors.value = result.minors
      listError.value = false
    } catch {
      listError.value = true
    }
    schedule()
  }

  function schedule() {
    if (timer) clearTimeout(timer)
    timer = undefined
    if (polling && pending.value) timer = setTimeout(load, POLL_MS)
  }

  function startPolling() {
    polling = true
    return load()
  }

  function stopPolling() {
    polling = false
    if (timer) clearTimeout(timer)
    timer = undefined
  }

  /** Validaciones de comodidad: la que cuenta es la del servidor. */
  function check(file: File): string {
    const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
    if (!ALLOWED.includes(extension)) return 'Tipo no admitido. Usa PDF, Word (.docx) o texto (.txt, .md).'
    if (file.size === 0) return 'El fichero está vacío.'
    if (file.size > MAX_BYTES) return 'El fichero supera el máximo de 10 MB.'
    return ''
  }

  /** Sube un fichero y devuelve el id del documento (F18: el asistente lo marca al estar listo); cadena vacía si no se subió. */
  async function upload(file: File): Promise<string> {
    if (uploading.value) return ''
    uploadError.value = check(file)
    if (uploadError.value) return ''
    uploading.value = true
    try {
      const created = await api.uploadMaterial(file)
      await load()
      return created?.id ?? ''
    } catch (e) {
      uploadError.value =
        (e instanceof ApiError && e.code && uploadMessages[e.code]) || 'No se pudo subir el documento. Inténtalo de nuevo.'
    } finally {
      uploading.value = false
    }
    return ''
  }

  async function loadUsage() {
    try {
      usage.value = await api.getMaterialUsage()
    } catch {
      usage.value = null
    }
  }

  async function remove(id: string) {
    deleteError.value = false
    try {
      await api.deleteMaterial(id)
      await load()
      void loadUsage()
    } catch {
      deleteError.value = true
    }
  }

  return { own, minors, listError, uploadError, deleteError, uploading, usage, pending, load, loadUsage, startPolling, stopPolling, upload, remove }
})
