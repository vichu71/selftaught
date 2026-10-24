import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, type User } from '@/api/client'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  /** true cuando ya se ha preguntado al servidor si hay sesión (evita repetir /api/me en cada ruta). */
  const ready = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const isTutor = computed(() => user.value?.roles.includes('TUTOR') ?? false)

  async function login(identifier: string, password: string) {
    user.value = await api.login(identifier, password)
  }

  async function logout() {
    try {
      await api.logout()
    } finally {
      user.value = null // aunque falle la llamada, esta pestaña queda sin sesión
    }
  }

  async function loadMe() {
    try {
      user.value = await api.me()
    } catch {
      user.value = null
    } finally {
      ready.value = true
    }
  }

  return { user, ready, isAuthenticated, isTutor, login, logout, loadMe }
})
