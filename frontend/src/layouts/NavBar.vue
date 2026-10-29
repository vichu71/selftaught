<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import Mascot from '@/world/Mascot.vue'
import { BookOpenText, LogOut, Moon, Map as MapIcon, BarChart3, ShieldCheck } from 'lucide-vue-next'
import { firstName } from './greeting'
import { zoneOf } from './zone'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

// «Mapa» agrupa todo lo que cuelga de los cursos; Material y Resultados son secciones propias (la zona sale de la ruta, F17)
const section = computed(() => zoneOf(route.path))
const greeting = computed(() => {
  const name = firstName(auth.user?.displayName)
  return name ? `¡Hola, ${name}!` : '¡Hola!'
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
        <RouterLink to="/" class="nav-link" data-zone="map" :class="{ 'is-active': section === 'map' }" :aria-current="section === 'map' ? 'page' : undefined" data-testid="nav-map">
          <span class="nav-bubble"><MapIcon :size="18" aria-hidden="true" /></span>Mapa
        </RouterLink>
        <RouterLink to="/material" class="nav-link" data-zone="material" :class="{ 'is-active': section === 'material' }" :aria-current="section === 'material' ? 'page' : undefined" data-testid="nav-material">
          <span class="nav-bubble"><BookOpenText :size="18" aria-hidden="true" /></span>Mi material
        </RouterLink>
        <RouterLink to="/resultados" class="nav-link" data-zone="results" :class="{ 'is-active': section === 'results' }" :aria-current="section === 'results' ? 'page' : undefined" data-testid="nav-results">
          <span class="nav-bubble"><BarChart3 :size="18" aria-hidden="true" /></span>Resultados
        </RouterLink>
        <RouterLink v-if="auth.user?.owner" to="/propietario" class="nav-link" data-zone="owner" :class="{ 'is-active': section === 'owner' }" :aria-current="section === 'owner' ? 'page' : undefined" data-testid="nav-owner">
          <span class="nav-bubble"><ShieldCheck :size="18" aria-hidden="true" /></span>Propietario
        </RouterLink>
      </nav>
      <div class="nav-user">
        <span class="nav-greeting" data-testid="nav-user"><span class="nav-user-name">{{ greeting }}</span></span>
        <button type="button" class="rest-button" data-testid="rest" aria-label="Dejarlo por hoy" @click="rest"><Moon :size="16" aria-hidden="true" /> <span class="rest-text">Dejarlo por hoy</span></button>
        <button type="button" data-testid="logout" @click="logout"><LogOut :size="16" aria-hidden="true" /> Cerrar sesión</button>
      </div>
    </div>
  </header>
</template>
