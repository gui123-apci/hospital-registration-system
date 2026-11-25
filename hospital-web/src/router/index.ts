import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routeDecision, sessionFailureDestination } from '@/router/guard'
import type { Role } from '@/types/models'
import AuthLayout from '@/layouts/AuthLayout.vue'
import PatientLayout from '@/layouts/PatientLayout.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import NotFound from '@/views/NotFound.vue'
import Forbidden from '@/views/Forbidden.vue'
import SessionError from '@/views/SessionError.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: AuthLayout,
      children: [{ path: '', name: 'login', component: PlaceholderView }]
    },
    {
      path: '/register',
      component: AuthLayout,
      children: [{ path: '', name: 'register', component: PlaceholderView }]
    },
    {
      path: '/patient',
      component: PatientLayout,
      meta: { requiresAuth: true, roles: [0] as Role[] },
      children: [{ path: 'home', name: 'patient-home', component: PlaceholderView }]
    },
    {
      path: '/doctor',
      component: DoctorLayout,
      meta: { requiresAuth: true, roles: [2] as Role[] },
      children: [{ path: 'workbench', name: 'doctor-workbench', component: PlaceholderView }]
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, roles: [1] as Role[] },
      children: [{ path: 'departments', name: 'admin-departments', component: PlaceholderView }]
    },
    { path: '/forbidden', name: 'forbidden', component: Forbidden },
    { path: '/session-error', name: 'session-error', component: SessionError },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFound }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) return '/login'
  const skipsRestoration = to.name === 'forbidden' || to.name === 'session-error'
  if (!skipsRestoration && auth.token && !auth.user) {
    try {
      await auth.restore()
    } catch (error) {
      const destination = sessionFailureDestination(error)
      if (destination === '/session-error') {
        return { path: destination, query: { redirect: to.fullPath } }
      }
      return destination
    }
  }
  const roles = to.meta.roles as Role[] | undefined
  return routeDecision(
    String(to.name ?? ''),
    Boolean(to.meta.requiresAuth),
    roles,
    auth
  )
})

export default router
