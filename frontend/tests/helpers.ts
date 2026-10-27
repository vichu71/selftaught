import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import type { Component } from 'vue'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'

/**
 * Contrato que fijan los tests del frontend:
 *  - '@/api/client' exporta `api` (un método por endpoint) y `ApiError` (con `status`).
 *  - '@/stores/auth' exporta `useAuthStore` con `user`, `ready`, `login`, `logout`, `loadMe`.
 *  - '@/router' exporta `createAppRouter(history)`; las rutas protegidas redirigen a /login.
 * Los elementos se localizan con data-testid para no depender del texto ni del estilo.
 */
export interface TestUser {
  id: string
  displayName: string
  kind: 'ADULT' | 'MINOR'
  roles: string[]
  email?: string
  loginId?: string
  /** F14: solo el propietario lo trae (el servidor lo calcula; el enlace del menú depende de él, el permiso no). */
  owner?: boolean
}

export const adult: TestUser = {
  id: 'u-ana', displayName: 'Ana', kind: 'ADULT', roles: ['STUDENT', 'TUTOR'], email: 'ana@example.com',
}
export const ownerUser: TestUser = { ...adult, owner: true }
export const minor: TestUser = {
  id: 'u-hugo', displayName: 'Hugo', kind: 'MINOR', roles: ['STUDENT'], loginId: 'hugo',
}

export async function mountApp(
  component: Component,
  opts: { route?: string; user?: TestUser | null } = {},
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const auth = useAuthStore()
  auth.user = opts.user ?? null
  auth.ready = true // la sesión ya se ha consultado: los tests de vistas no pasan por /api/me
  await router.push(opts.route ?? '/')
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth }
}

export const byId = (wrapper: ReturnType<typeof mount>, id: string) => wrapper.find(`[data-testid="${id}"]`)
