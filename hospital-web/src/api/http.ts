import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types/models'

const TOKEN_KEY = 'hospital_token'

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
    const payload = response.data as Result<unknown>
    if (payload.code !== 200) {
      throw new ApiError(payload.code, payload.msg || '操作失败', response.status)
    }
    return payload.data
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const payload = error.response?.data as Result<unknown> | undefined
    if (status === 401) {
      sessionStorage.removeItem(TOKEN_KEY)
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    if (status === 403 && window.location.pathname !== '/forbidden') {
      window.location.assign('/forbidden')
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
  get<T>(url: string, config?: AxiosRequestConfig) {
    return instance.get<unknown, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.post<unknown, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.put<unknown, T>(url, data, config)
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return instance.delete<unknown, T>(url, config)
  }
}

export { TOKEN_KEY }
