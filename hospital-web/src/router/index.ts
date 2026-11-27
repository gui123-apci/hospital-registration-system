import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { AuthFailureKind } from '@/api/http'
import { routeDecision, sessionFailureDestination } from '@/router/guard'
import type { Role } from '@/types/models'
import AuthLayout from '@/layouts/AuthLayout.vue'
import PatientLayout from '@/layouts/PatientLayout.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import NotFound from '@/views/NotFound.vue'
import Forbidden from '@/views/Forbidden.vue'
import SessionError from '@/views/SessionError.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: AuthLayout,
      children: [{ path: '', name: 'login', component: () => import('@/views/auth/LoginView.vue') }]
    },
    {
      path: '/register',
      component: AuthLayout,
      children: [{ path: '', name: 'register', component: () => import('@/views/auth/RegisterView.vue') }]
    },
    {
      path: '/patient',
      component: PatientLayout,
      meta: { requiresAuth: true, roles: [0] as Role[] },
      redirect: '/patient/home',
      children: [
        { path: 'home', name: 'patient-home', component: () => import('@/views/patient/HomeView.vue') },
        { path: 'departments', name: 'patient-departments', component: () => import('@/views/patient/DepartmentListView.vue') },
        { path: 'doctors', name: 'patient-doctors', component: () => import('@/views/patient/DoctorListView.vue') },
        { path: 'schedules', name: 'patient-schedules', component: () => import('@/views/patient/ScheduleListView.vue') },
        { path: 'booking', name: 'patient-booking', component: () => import('@/views/patient/BookingView.vue') },
        { path: 'orders', name: 'patient-orders', component: () => import('@/views/patient/OrderListView.vue') },
        { path: 'payment', name: 'patient-payment', component: () => import('@/views/patient/PaymentView.vue') },
        { path: 'patients', name: 'patient-patients', component: () => import('@/views/patient/PatientListView.vue') },
        { path: 'reports', name: 'patient-reports', component: () => import('@/views/patient/ReportListView.vue') },
        { path: 'triage', name: 'patient-triage', component: () => import('@/views/patient/TriageView.vue') }
      ]
    },
    {
      path: '/doctor',
      component: DoctorLayout,
      meta: { requiresAuth: true, roles: [2] as Role[] },
      redirect: '/doctor/workbench',
      children: [
        { path: 'workbench', name: 'doctor-workbench', component: () => import('@/views/doctor/WorkbenchView.vue') },
        { path: 'schedules', name: 'doctor-schedules', component: () => import('@/views/doctor/ScheduleView.vue') }
      ]
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, roles: [1] as Role[] },
      redirect: '/admin/departments',
      children: [
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'departments', name: 'admin-departments', component: () => import('@/views/admin/DepartmentManageView.vue') },
        { path: 'doctors', name: 'admin-doctors', component: () => import('@/views/admin/DoctorManageView.vue') },
        { path: 'schedules', name: 'admin-schedules', component: () => import('@/views/admin/ScheduleManageView.vue') }
      ]
    },
    { path: '/forbidden', name: 'forbidden', component: Forbidden },
    { path: '/session-error', name: 'session-error', component: SessionError },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFound }
  ]
})

/**
 * 接口层报上来的认证失败：这里统一决定去向，替代原来 http.ts 里的整页跳转。
 *
 * 会话恢复（守卫里的 auth.restore）期间的失败不在这里跳：
 * 那时 user 还是空的，由守卫根据 401/403/网络错误给出更准确的目的地，避免双重跳转。
 * 由 main.ts 注册到 http 层。
 */
export function handleAuthFailure(kind: AuthFailureKind) {
  const auth = useAuthStore()
  const current = router.currentRoute.value
  const hadSession = Boolean(auth.user)
  if (kind === 'unauthorized') {
    auth.logout()
    if (!hadSession || current.path === '/login') return
    void router.replace('/login')
    return
  }
  if (!hadSession || current.path === '/forbidden') return
  void router.replace('/forbidden')
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // 根路径没有页面，按登录态分流：未登录去登录页，已登录回各自角色首页
  const isRoot = to.path === '/'
  if ((to.meta.requiresAuth || isRoot) && !auth.token) return '/login'
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
  if (isRoot) return auth.homePath
  const roles = to.meta.roles as Role[] | undefined
  return routeDecision(
    String(to.name ?? ''),
    Boolean(to.meta.requiresAuth),
    roles,
    auth
  )
})

export default router
