import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { ApiError, setAuthFailureHandler } from '@/api/http'
import { orderApi } from '@/api/order'
import { serverNow } from '@/utils/serverClock'
import type { Order, Result } from '@/types/models'

const axiosMock = vi.hoisted(() => ({
  post: vi.fn(),
  onFulfilled: undefined as ((response: {
    data: unknown
    status: number
    config: { unwrap?: boolean }
    headers?: Record<string, string>
  }) => unknown) | undefined,
  onRejected: undefined as ((error: unknown) => Promise<unknown>) | undefined
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: {
          use: vi.fn((onFulfilled: typeof axiosMock.onFulfilled, onRejected: typeof axiosMock.onRejected) => {
            axiosMock.onFulfilled = onFulfilled
            axiosMock.onRejected = onRejected
          })
        }
      },
      get: vi.fn(),
      post: axiosMock.post,
      put: vi.fn(),
      delete: vi.fn()
    }))
  }
}))

function dispatchResponse(
  payload: Result<unknown>,
  config: { unwrap?: boolean } = {},
  headers?: Record<string, string>
) {
  if (!axiosMock.onFulfilled) throw new Error('Response interceptor was not registered')
  return axiosMock.onFulfilled({ data: payload, status: 200, config, headers })
}

function dispatchHttpError(status: number, payload?: unknown) {
  if (!axiosMock.onRejected) throw new Error('Response error interceptor was not registered')
  return axiosMock.onRejected({ response: { status, data: payload } })
}

describe('http client contract', () => {
  beforeEach(() => {
    axiosMock.post.mockReset()
  })

  it('exposes business error code and message', () => {
    const error = new ApiError(2051, '该时段号源已约满')
    expect(error.code).toBe(2051)
    expect(error.message).toBe('该时段号源已约满')
  })

  it('unwraps Result.data by default', () => {
    const data = { id: 9 }
    expect(dispatchResponse({ code: 200, msg: '成功', data })).toBe(data)
  })

  it('preserves the full Result envelope only when unwrap is false', () => {
    const envelope = { code: 200, msg: '挂号成功，请完成支付', data: { id: 99 } }
    expect(dispatchResponse(envelope, { unwrap: false })).toBe(envelope)
  })

  it.each([
    ['default mode', {}],
    ['envelope mode', { unwrap: false }]
  ])('throws ApiError for business failures in %s', (_label, config) => {
    let thrown: unknown
    try {
      dispatchResponse({ code: 2051, msg: '该时段号源已约满', data: null }, config)
    } catch (reason) {
      thrown = reason
    }

    expect(thrown).toBeInstanceOf(ApiError)
    expect(thrown).toMatchObject({
      code: 2051,
      message: '该时段号源已约满',
      httpStatus: 200
    })
  })
})

describe('authentication failures', () => {
  afterEach(() => {
    setAuthFailureHandler(null)
    sessionStorage.clear()
  })

  it('reports a 401 to the registered handler and clears the stored token', async () => {
    const handler = vi.fn()
    setAuthFailureHandler(handler)
    sessionStorage.setItem('hospital_token', 'stale-token')

    await expect(dispatchHttpError(401, { code: 3001, msg: '未登录或登录已失效' }))
      .rejects.toMatchObject({ code: 3001, message: '未登录或登录已失效', httpStatus: 401 })

    expect(sessionStorage.getItem('hospital_token')).toBeNull()
    expect(handler).toHaveBeenCalledWith('unauthorized')
  })

  it('reports a 403 without clearing the token', async () => {
    const handler = vi.fn()
    setAuthFailureHandler(handler)
    sessionStorage.setItem('hospital_token', 'still-valid')

    await expect(dispatchHttpError(403, { code: 3002, msg: '没有权限访问该资源' }))
      .rejects.toBeInstanceOf(ApiError)

    expect(sessionStorage.getItem('hospital_token')).toBe('still-valid')
    expect(handler).toHaveBeenCalledWith('forbidden')
  })

  it('rejects without touching navigation when no handler is registered', async () => {
    setAuthFailureHandler(null)

    await expect(dispatchHttpError(401))
      .rejects.toMatchObject({ code: 401, httpStatus: 401 })
  })
})

describe('server clock alignment', () => {
  it('aligns serverNow with the Date header of the response', () => {
    const serverDate = new Date(Date.now() + 5 * 60_000)

    dispatchResponse({ code: 200, msg: '成功', data: null }, {}, { date: serverDate.toUTCString() })

    expect(Math.abs(serverNow().getTime() - serverDate.getTime())).toBeLessThan(2000)
  })
})

describe('order API contract', () => {
  const order: Order = {
    id: 99,
    orderNo: 'ORDER-99',
    patientId: 17,
    patientName: '李患者',
    scheduleId: 41,
    workDate: '2026-11-26',
    timeSlot: 1,
    amount: 36,
    status: 0,
    statusDesc: '待支付',
    createTime: '2026-11-25 10:00:00'
  }

  beforeEach(() => {
    vi.clearAllMocks()
    axiosMock.post.mockReset()
  })

  it('requests the response envelope and returns the order with the backend message', async () => {
    axiosMock.post.mockResolvedValue({ code: 200, msg: '挂号成功，请在30分钟内支付', data: order })

    const result = await orderApi.create({ scheduleId: 41, patientId: 17 })

    expect(axiosMock.post).toHaveBeenCalledWith(
      '/order',
      { scheduleId: 41, patientId: 17 },
      { unwrap: false }
    )
    expect(result).toEqual({ order, message: '挂号成功，请在30分钟内支付' })
  })
})

it('creates an axios instance with the shared client configuration', async () => {
  vi.mocked(axios.create).mockClear()
  vi.resetModules()
  const { http: freshHttp } = await import('@/api/http')

  expect(freshHttp).toBeDefined()
  expect(axios.create).toHaveBeenCalledWith({ baseURL: '/api', timeout: 15000 })
})
