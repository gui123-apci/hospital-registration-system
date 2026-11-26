import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import App from '@/App.vue'
import { routeDecision, type GuardAuth } from '@/router/guard'
import router, { handleAuthFailure } from '@/router'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import type { Role, UserInfo } from '@/types/models'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  me: vi.fn()
}))

function auth(role: Role | null, authenticated = true): GuardAuth {
  const user: UserInfo | null = role === null ? null : {
    id: 1,
    username: 'test',
    role,
    roleName: '测试'
  }
  return {
    token: authenticated ? 'token' : '',
    user,
    isAuthenticated: authenticated && Boolean(user),
    homePath: role === 0 ? '/patient/home' : role === 1 ? '/admin/departments' : role === 2 ? '/doctor/workbench' : '/login'
  }
}

describe('route guard', () => {
  it('redirects unauthenticated users to login', () => {
    expect(routeDecision('patient-home', true, [0], auth(null, false))).toBe('/login')
  })

  it('redirects a role mismatch to that role home', () => {
    expect(routeDecision('admin-departments', true, [1], auth(0))).toBe('/patient/home')
  })

  it('redirects authenticated users away from login', () => {
    expect(routeDecision('login', false, undefined, auth(2))).toBe('/doctor/workbench')
  })

  it('allows a matching role', () => {
    expect(routeDecision('admin-departments', true, [1], auth(1))).toBe(true)
  })
})

describe('session restoration routing', () => {
  let pinia: Pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)
    sessionStorage.clear()
    sessionStorage.setItem('hospital_token', 'stored-token')
    vi.clearAllMocks()
    // 复位到不需要恢复会话的页面，避免上一个用例停留的路径让后续 push 变成重复导航
    await router.replace('/forbidden')
  })

  it('clears auth and selects login for a 401', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(3001, '未登录', 401))

    await router.push('/patient/home')

    const authStore = useAuthStore()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(authStore.token).toBe('')
    expect(authStore.user).toBeNull()
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
  }, 20000)

  it('retains auth and selects forbidden for a 403', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(3002, '无权限', 403))

    await router.push('/patient/home')

    const authStore = useAuthStore()
    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(authStore.token).toBe('stored-token')
    expect(authStore.user).toBeNull()
    expect(sessionStorage.getItem('hospital_token')).toBe('stored-token')
    expect(authApi.me).toHaveBeenCalledTimes(1)
  })

  it('routes the root path to login when there is no token', async () => {
    sessionStorage.clear()

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(authApi.me).not.toHaveBeenCalled()
  })

  it('routes the root path to the role home after restoring the session', async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      id: 2,
      username: 'doctor001',
      role: 2,
      roleName: '医生'
    })

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/doctor/workbench')
    expect(authApi.me).toHaveBeenCalledTimes(1)
  })

  it('resolves a bare section path to its default page', async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      id: 3,
      username: 'patient',
      role: 0,
      roleName: '患者'
    })

    await router.push('/patient')

    expect(router.currentRoute.value.path).toBe('/patient/home')
  })

  it.each([
    ['network', new Error('Network Error')],
    ['server', new ApiError(5000, '服务暂不可用', 503)]
  ])('retains auth and selects a retryable destination for a transient %s failure', async (_label, error) => {
    vi.mocked(authApi.me).mockRejectedValue(error)

    await router.push('/patient/home')

    const authStore = useAuthStore()
    expect(router.currentRoute.value.path).toBe('/session-error')
    expect(router.currentRoute.value.query.redirect).toBe('/patient/home')
    expect(authStore.token).toBe('stored-token')
    expect(authStore.user).toBeNull()
    expect(sessionStorage.getItem('hospital_token')).toBe('stored-token')
    expect(authApi.me).toHaveBeenCalledTimes(1)
  })

  it('retries restoration and returns to the requested page', async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      id: 1,
      username: 'patient',
      role: 0,
      roleName: '患者'
    })
    await router.push({ path: '/session-error', query: { redirect: '/patient/home' } })
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await wrapper.get('button').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.path).toBe('/patient/home')
    }, { timeout: 10000 })

    expect(router.currentRoute.value.path).toBe('/patient/home')
    expect(useAuthStore().user?.role).toBe(0)
    expect(sessionStorage.getItem('hospital_token')).toBe('stored-token')
  }, 15000)
})

describe('authentication failure handling', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    sessionStorage.setItem('hospital_token', 'stored-token')
    vi.clearAllMocks()
    await router.replace('/forbidden')
  })

  async function signIn(role: Role = 0) {
    const authStore = useAuthStore()
    authStore.token = 'stored-token'
    authStore.user = { id: 1, username: 'patient', role, roleName: '测试' }
    await router.replace('/patient/home')
    return authStore
  }

  it('routes a mid-session 401 to login and clears the session', async () => {
    const authStore = await signIn()

    handleAuthFailure('unauthorized')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.path).toBe('/login')
    })

    expect(authStore.token).toBe('')
    expect(authStore.user).toBeNull()
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
  })

  it('routes a mid-session 403 to forbidden but keeps the session', async () => {
    const authStore = await signIn()

    handleAuthFailure('forbidden')
    await vi.waitFor(() => {
      expect(router.currentRoute.value.path).toBe('/forbidden')
    })

    expect(authStore.token).toBe('stored-token')
    expect(authStore.user).not.toBeNull()
    expect(sessionStorage.getItem('hospital_token')).toBe('stored-token')
  })

  it('leaves the destination to the guard when restoration is still in progress', async () => {
    const authStore = useAuthStore()
    authStore.token = 'stored-token'
    expect(authStore.user).toBeNull()

    handleAuthFailure('unauthorized')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(authStore.token).toBe('')
  })
})
