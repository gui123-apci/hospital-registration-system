import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { ApiError, http } from '@/api/http'

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    }))
  }
}))

describe('http client contract', () => {
  beforeEach(() => vi.clearAllMocks())

  it('exposes business error code and message', () => {
    const error = new ApiError(2051, '该时段号源已约满')
    expect(error.code).toBe(2051)
    expect(error.message).toBe('该时段号源已约满')
  })

  it('creates an axios instance', async () => {
    expect(http).toBeDefined()
    vi.resetModules()
    await import('@/api/http')
    expect(axios.create).toHaveBeenCalledWith({ baseURL: '/api', timeout: 15000 })
  })
})
