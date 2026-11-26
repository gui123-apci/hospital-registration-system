import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types/models'
import { syncServerClock } from '@/utils/serverClock'

const TOKEN_KEY = 'hospital_token'

/** 认证失败类型：401 未登录/登录失效，403 已登录但无权限 */
export type AuthFailureKind = 'unauthorized' | 'forbidden'

let authFailureHandler: ((kind: AuthFailureKind) => void) | null = null

/**
 * 注册认证失败回调。
 *
 * 传输层只报告"这个请求没通过认证"，清会话与跳转交给路由层统一决定：
 * 这里如果直接 window.location 整页刷新，会和路由守卫各跳一次，还会丢掉 SPA 状态。
 */
export function setAuthFailureHandler(handler: ((kind: AuthFailureKind) => void) | null) {
  authFailureHandler = handler
}

export interface HttpRequestConfig extends AxiosRequestConfig {
  unwrap?: boolean
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public httpStatus?: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const instance = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api', timeout: 15000 })

instance.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

instance.interceptors.response.use(
  // @ts-expect-error Axios 1.21 types require an AxiosResponse, but this interceptor intentionally unwraps Result.data.
  (response) => {
    // 每个响应都带服务端 Date，顺手校正本地时钟，退号/签到按时段判断才有准头
    syncServerClock(response.headers?.date)
    const payload = response.data as Result<unknown>
    if (payload.code !== 200) {
      throw new ApiError(payload.code, payload.msg || '操作失败', response.status)
    }
    return (response.config as HttpRequestConfig).unwrap === false ? payload : payload.data
  },
  (error) => {
    syncServerClock(error.response?.headers?.date)
    const status = error.response?.status as number | undefined
    const payload = error.response?.data as Result<unknown> | undefined
    if (status === 401) {
      sessionStorage.removeItem(TOKEN_KEY)
      authFailureHandler?.('unauthorized')
    } else if (status === 403) {
      authFailureHandler?.('forbidden')
    }
    return Promise.reject(
      new ApiError(
        payload?.code ?? status ?? 5000,
        payload?.msg ?? '网络异常，请稍后重试',
        status
      )
    )
  }
)

export const http = {
  get<T>(url: string, config?: HttpRequestConfig) {
    return instance.get<unknown, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: HttpRequestConfig) {
    return instance.post<unknown, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: HttpRequestConfig) {
    return instance.put<unknown, T>(url, data, config)
  },
  delete<T>(url: string, config?: HttpRequestConfig) {
    return instance.delete<unknown, T>(url, config)
  }
}

export { TOKEN_KEY }
