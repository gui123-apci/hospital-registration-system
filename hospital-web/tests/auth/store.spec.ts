import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  me: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('stores token and user after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'token-1',
      tokenType: 'Bearer',
      expiresIn: 100,
      user: { id: 1, username: '13800000000', role: 0, roleName: '患者' }
    })
    const store = useAuthStore()
    await store.login('13800000000', '123456')
    expect(store.token).toBe('token-1')
    expect(store.user?.role).toBe(0)
    expect(sessionStorage.getItem('hospital_token')).toBe('token-1')
  })

  it('clears session on logout', () => {
    sessionStorage.setItem('hospital_token', 'token-1')
    sessionStorage.setItem('doctor-work:active-consultation', '{"userId":9}')
    const store = useAuthStore()
    store.logout()
    expect(store.token).toBe('')
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
    expect(sessionStorage.getItem('doctor-work:active-consultation')).toBeNull()
  })

  it('clears persisted and in-memory auth state for a 401 restoration failure', async () => {
    sessionStorage.setItem('hospital_token', 'expired-token')
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(3001, '未登录', 401))
    const store = useAuthStore()

    await expect(store.restore()).rejects.toThrow('未登录')

    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(store.loading).toBe(false)
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
  })

  it('retains persisted and in-memory auth state for a 403 restoration failure', async () => {
    sessionStorage.setItem('hospital_token', 'forbidden-token')
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(3002, '无权限', 403))
    const store = useAuthStore()

    await expect(store.restore()).rejects.toThrow('无权限')

    expect(store.token).toBe('forbidden-token')
    expect(store.user).toBeNull()
    expect(store.loading).toBe(false)
    expect(sessionStorage.getItem('hospital_token')).toBe('forbidden-token')
  })

  it.each([
    ['a network failure', new Error('Network Error')],
    ['a server failure', new ApiError(5000, '服务暂不可用', 503)]
  ])('retains auth state for %s during restoration', async (_label, error) => {
    sessionStorage.setItem('hospital_token', 'valid-token')
    vi.mocked(authApi.me).mockRejectedValue(error)
    const store = useAuthStore()

    await expect(store.restore()).rejects.toBe(error)

    expect(store.token).toBe('valid-token')
    expect(store.user).toBeNull()
    expect(store.loading).toBe(false)
    expect(sessionStorage.getItem('hospital_token')).toBe('valid-token')
  })
})
