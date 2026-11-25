import { ApiError } from '@/api/http'
import type { Role, UserInfo } from '@/types/models'

export interface GuardAuth {
  token: string
  user: UserInfo | null
  isAuthenticated: boolean
  homePath: string
}

export function routeDecision(
  toName: string,
  requiresAuth: boolean,
  roles: Role[] | undefined,
  auth: GuardAuth
): string | true {
  if (requiresAuth && !auth.token) return '/login'
  if (roles && auth.user && !roles.includes(auth.user.role)) return auth.homePath
  if ((toName === 'login' || toName === 'register') && auth.isAuthenticated) return auth.homePath
  return true
}

export function sessionFailureDestination(error: unknown): '/login' | '/forbidden' | '/session-error' {
  if (error instanceof ApiError) {
    if (error.httpStatus === 401) return '/login'
    if (error.httpStatus === 403) return '/forbidden'
  }
  return '/session-error'
}
