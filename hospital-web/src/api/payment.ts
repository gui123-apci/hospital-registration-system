import { http } from '@/api/http'

export const paymentApi = {
  pay(orderId: number, channel = 0) {
    return http.post<void>('/payment/pay', { orderId, channel })
  },
  refund(orderId: number) {
    return http.post<void>('/payment/refund', { orderId })
  }
}
