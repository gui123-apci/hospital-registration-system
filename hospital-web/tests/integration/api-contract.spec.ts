import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/api/http'
import { authApi } from '@/api/auth'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import { doctorWorkApi } from '@/api/doctorWork'
import { adminScheduleApi } from '@/api/adminSchedule'
import type { Order, Result } from '@/types/models'

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

const createdOrder: Order = {
  id: 9,
  orderNo: 'ORDER-9',
  patientId: 12,
  patientName: '李患者',
  scheduleId: 41,
  workDate: '2026-11-25',
  timeSlot: 1,
  amount: 30,
  status: 0,
  statusDesc: '待支付',
  deadlineTime: '2026-11-25T09:05:00',
  createTime: '2026-11-25T08:55:00'
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(http.get).mockResolvedValue(undefined)
  vi.mocked(http.post).mockResolvedValue(undefined)
  vi.mocked(http.put).mockResolvedValue(undefined)
  vi.mocked(http.delete).mockResolvedValue(undefined)
})

describe('backend API contract', () => {
  it('uses bearer-auth endpoints without hardcoded user ids', async () => {
    await authApi.login({ username: '13800000000', password: '123456' })
    await authApi.me()

    expect(http.post).toHaveBeenCalledWith('/auth/login', { username: '13800000000', password: '123456' })
    expect(http.get).toHaveBeenCalledWith('/auth/me')
  })

  it('creates an order with numeric schedule and patient ids in the body and reads the full Result envelope', async () => {
    const envelope: Result<Order> = { code: 200, msg: '挂号成功', data: createdOrder }
    vi.mocked(http.post).mockResolvedValueOnce(envelope)

    const result = await orderApi.create({ scheduleId: 9, patientId: 12 })

    expect(http.post).toHaveBeenCalledWith('/order', { scheduleId: 9, patientId: 12 }, { unwrap: false })
    expect(result.order).toEqual(createdOrder)
    expect(result.message).toBe('挂号成功')
  })

  it('reads a single order by id for the payment page', async () => {
    await orderApi.get(9)

    expect(http.get).toHaveBeenCalledWith('/order/9')
  })

  it('uses numeric payment ids in the request body', async () => {
    await paymentApi.pay(9, 0)
    await paymentApi.refund(12)

    expect(http.post).toHaveBeenCalledWith('/payment/pay', { orderId: 9, channel: 0 })
    expect(http.post).toHaveBeenCalledWith('/payment/refund', { orderId: 12 })
  })

  it('uses the existing doctor-work parameter locations and numeric ids', async () => {
    await doctorWorkApi.waiting(9)
    await doctorWorkApi.call(9)
    await doctorWorkApi.report({ orderId: 12, title: '血常规' })

    expect(http.get).toHaveBeenCalledWith('/doctor-work/waiting', { params: { scheduleId: 9 } })
    expect(http.post).toHaveBeenCalledWith('/doctor-work/call', null, { params: { scheduleId: 9 } })
    expect(http.post).toHaveBeenCalledWith('/doctor-work/report', { orderId: 12, title: '血常规' })
  })

  it('creates and updates schedules with numeric ids and no departmentId', async () => {
    const createPayload = {
      doctorId: 21,
      workDate: '2026-11-25',
      timeSlot: 1 as const,
      totalNum: 20,
      price: 30,
      status: 1 as const
    }
    const updatePayload = {
      id: 9,
      workDate: '2026-11-26',
      timeSlot: 2 as const,
      totalNum: 25,
      price: 32.5,
      status: 1 as const
    }

    await adminScheduleApi.create(createPayload)
    await adminScheduleApi.update(updatePayload)

    expect(http.post).toHaveBeenCalledWith('/admin/schedule', createPayload)
    expect(vi.mocked(http.post).mock.calls[0][1]).not.toHaveProperty('departmentId')
    expect(http.put).toHaveBeenCalledWith('/admin/schedule', updatePayload)
  })

  it('places schedule filters in query params and numeric schedule ids in the delete path', async () => {
    await adminScheduleApi.page(2, 20, 21)
    await adminScheduleApi.page()
    await adminScheduleApi.remove(9)

    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', {
      params: { pageNum: 2, pageSize: 20, doctorId: 21 }
    })
    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', {
      params: { pageNum: 1, pageSize: 10 }
    })
    expect(http.delete).toHaveBeenCalledWith('/admin/schedule/9')
  })
})
