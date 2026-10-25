import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import MaterialView from '@/views/MaterialView.vue'
import NewCourseView from '@/views/NewCourseView.vue'
import CourseStatusView from '@/views/CourseStatusView.vue'
import PieceView from '@/views/PieceView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import VerifyEmailView from '@/views/VerifyEmailView.vue'

/**
 * Rutas protegidas por defecto: solo son públicas las marcadas con meta.public.
 * Las vistas se importan directamente (sin carga diferida): la app es pequeña y la navegación es inmediata.
 */
export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/', name: 'home', component: HomeView },
      { path: '/material', name: 'material', component: MaterialView },
      { path: '/nuevo-curso', name: 'new-course', component: NewCourseView },
      { path: '/cursos/:id', name: 'course', component: CourseStatusView },
      { path: '/cursos/:id/piezas/:pieceId', name: 'piece', component: PieceView },
      { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
      { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
      { path: '/verify-email', name: 'verify-email', component: VerifyEmailView, meta: { public: true } },
      { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
      { path: '/reset-password', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
      { path: '/:pathMatch(.*)*', redirect: '/' },
    ],
  })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.ready) await auth.loadMe()

    if (!to.meta.public && !auth.isAuthenticated) return { name: 'login' }
    if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) return { name: 'home' }
    return true
  })

  return router
}
