import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { ApiError, TOKEN_KEY } from '@/api/http'
import type { Role, UserInfo } from '@/types/models'

const roleHome: Record<Role, string> = {
  0: '/patient/home',
  1: '/admin/departments',
  2: '/doctor/workbench'
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  const homePath = computed(() => user.value ? roleHome[user.value.role] : '/login')

  async function login(username: string, password: string) {
    const result = await authApi.login({ username, password })
    token.value = result.token
    user.value = result.user
    sessionStorage.setItem(TOKEN_KEY, result.token)
    return result
  }

  async function register(phone: string, password: string) {
    await authApi.register({ phone, password })
    return login(phone, password)
  }

  async function restore() {
    if (!token.value || user.value) return
    loading.value = true
    try {
      user.value = await authApi.me()
    } catch (error) {
      if (error instanceof ApiError && error.httpStatus === 401) logout()
      throw error
    } finally {
      loading.value = false
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem('doctor-work:active-consultation')
  }

  return { token, user, loading, isAuthenticated, homePath, login, register, restore, logout }
})
