/**
 * Cliente de la API. La sesión viaja en una cookie HttpOnly que el navegador envía solo (mismo origen);
 * en las peticiones que modifican datos se reenvía el token CSRF en la cabecera X-XSRF-TOKEN.
 * Las credenciales van siempre en el cuerpo, nunca en la URL.
 */
export interface User {
  id: string
  displayName: string
  kind: 'ADULT' | 'MINOR'
  roles: string[]
  email?: string
  loginId?: string
}

export interface Student {
  id: string
  displayName: string
  loginId: string
  status: string
}

export interface NewStudent {
  displayName: string
  loginHint: string
  password: string
}

export type MaterialStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'ERROR'

export interface MaterialDocument {
  id: string
  name: string
  mediaType: 'PDF' | 'DOCX' | 'TEXT'
  sizeBytes: number
  status: MaterialStatus
  errorKind?: string
  chunkCount: number
  pageCount?: number
  createdAt: string
  ownedByMe: boolean
}

export interface MinorMaterial {
  student: { id: string; displayName: string }
  documents: MaterialDocument[]
}

export interface MaterialList {
  own: MaterialDocument[]
  minors: MinorMaterial[]
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(`HTTP ${status}${code ? ` ${code}` : ''}`)
    this.name = 'ApiError'
  }
}

function csrfToken(): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return entry ? decodeURIComponent(entry.slice('XSRF-TOKEN='.length)) : undefined
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData
  // Con FormData el navegador pone él mismo el Content-Type (con el boundary): no se fija a mano
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const init: RequestInit = { method, headers, credentials: 'same-origin' }
  if (body !== undefined) init.body = isForm ? (body as FormData) : JSON.stringify(body)
  const response = await fetch(path, init)

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiError(response.status, error?.code)
  }
  if (response.status === 204) return undefined as T
  return (await response.json().catch(() => undefined)) as T
}

export const api = {
  register: (data: { email: string; password: string; displayName: string }) =>
    request<void>('POST', '/api/auth/register', data),
  verifyEmail: (token: string) => request<void>('POST', '/api/auth/verify-email', { token }),
  resendVerification: (email: string) => request<void>('POST', '/api/auth/resend-verification', { email }),
  login: (identifier: string, password: string) =>
    request<User>('POST', '/api/auth/login', { identifier, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  forgotPassword: (email: string) => request<void>('POST', '/api/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('POST', '/api/auth/reset-password', { token, newPassword }),
  me: () => request<User>('GET', '/api/me'),

  listStudents: () => request<Student[]>('GET', '/api/tutor/students'),
  createStudent: (data: NewStudent) => request<Student>('POST', '/api/tutor/students', data),
  resetStudentPassword: (id: string, newPassword: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/reset-password`, { newPassword }),
  deactivateStudent: (id: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/deactivate`),
  reactivateStudent: (id: string) =>
    request<void>('POST', `/api/tutor/students/${encodeURIComponent(id)}/reactivate`),

  listMaterial: () => request<MaterialList>('GET', '/api/material'),
  uploadMaterial: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<MaterialDocument>('POST', '/api/material', form)
  },
  deleteMaterial: (id: string) => request<void>('DELETE', `/api/material/${encodeURIComponent(id)}`),
}
