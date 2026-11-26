import { http } from '@/api/http'
import type { LoginResponse, UserInfo } from '@/types/models'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  phone: string
  password: string
}

export const authApi = {
  login(data: LoginRequest) {
    return http.post<LoginResponse>('/auth/login', data)
  },
  register(data: RegisterRequest) {
    return http.post<UserInfo>('/auth/register', data)
  },
  me() {
    return http.get<UserInfo>('/auth/me')
  }
}

export const login = authApi.login
export const register = authApi.register
export const me = authApi.me
