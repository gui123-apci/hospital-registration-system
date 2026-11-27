import { http } from '@/api/http'
import type { Order, OrderStatus, PageResult, Result } from '@/types/models'

export interface CreateOrderRequest {
  scheduleId: number
  patientId: number
  /** 幂等号：同一笔下单重试时复用，服务端据此避免重复建单 */
  requestId?: string
}

export interface CreateOrderResult {
  order: Order
  message: string
}

export const orderApi = {
  async create(data: CreateOrderRequest): Promise<CreateOrderResult> {
    const result = await http.post<Result<Order>>('/order', data, { unwrap: false })
    return { order: result.data, message: result.msg }
  },
  list(pageNum = 1, pageSize = 10, status?: OrderStatus) {
    return http.get<PageResult<Order>>('/order/list', {
      // status 为 undefined 时整个键都不带，后端按"不筛选"处理；传 null 会被序列化成字符串 "null"
      params: { pageNum, pageSize, ...(status === undefined ? {} : { status }) }
    })
  },
  get(orderId: number) {
    return http.get<Order>(`/order/${orderId}`)
  },
  sign(orderId: number) {
    return http.post<void>('/order/sign', null, { params: { orderId } })
  }
}
