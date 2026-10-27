<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import Mascot from '@/world/Mascot.vue'
import { BookOpenText, LogOut, Moon, Map as MapIcon, BarChart3, ShieldCheck } from 'lucide-vue-next'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

// «Mapa» agrupa todo lo que cuelga de los cursos; Material y Resultados son secciones propias
const section = computed<'map' | 'material' | 'results' | 'owner'>(() => {
  if (route.path.startsWith('/propietario')) return 'owner'
  if (route.path.startsWith('/material')) return 'material'
  if (route.path.startsWith('/resultados')) return 'results'
  return 'map'
})

// «Dejarlo por hoy»: monta de nuevo la vista (cierra paneles, diálogos y formularios abiertos) y vuelve al Inicio sin cerrar la sesión
async function rest() {
  ui.rest()
  ;(document.activeElement as HTMLElement | null)?.blur()
  await router.push('/')
}

async function logout() {
  await auth.logout().catch(() => undefined)
  await router.push('/login')
}
</script>

<template>
  <header class="nav-bar" data-testid="nav-bar">
    <div class="nav-inner">
      <RouterLink to="/" class="brand"><Mascot class="brand-mascot" data-testid="nav-mascot" />Study Lab</RouterLink>
      <nav class="nav-links" aria-label="Principal">
        <RouterLink to="/" class="nav-link" :class="{ 'is-active': section === 'map' }" :aria-current="section === 'map' ? 'page' : undefined" data-testid="nav-map">
          <MapIcon :size="18" aria-hidden="true" />&nbsp;Mapa
        </RouterLink>
        <RouterLink to="/material" class="nav-link" :class="{ 'is-active': section === 'material' }" :aria-current="section === 'material' ? 'page' : undefined" data-testid="nav-material">
          <BookOpenText :size="18" aria-hidden="true" />&nbsp;Mi material
        </RouterLink>
        <RouterLink to="/resultados" class="nav-link" :class="{ 'is-active': section === 'results' }" :aria-current="section === 'results' ? 'page' : undefined" data-testid="nav-results">
          <BarChart3 :size="18" aria-hidden="true" />&nbsp;Resultados
        </RouterLink>
        <RouterLink v-if="auth.user?.owner" to="/propietario" class="nav-link" :class="{ 'is-active': section === 'owner' }" :aria-current="section === 'owner' ? 'page' : undefined" data-testid="nav-owner">
          <ShieldCheck :size="18" aria-hidden="true" />&nbsp;Propietario
        </RouterLink>
      </nav>
      <div class="nav-user">
        <span data-testid="nav-user"><span class="nav-user-name">{{ auth.user?.displayName }}</span></span>
        <button type="button" class="rest-button" data-testid="rest" aria-label="Dejarlo por hoy" @click="rest"><Moon :size="16" aria-hidden="true" /> <span class="rest-text">Dejarlo por hoy</span></button>
        <button type="button" data-testid="logout" @click="logout"><LogOut :size="16" aria-hidden="true" /> Cerrar sesión</button>
      </div>
    </div>
  </header>
</template>
