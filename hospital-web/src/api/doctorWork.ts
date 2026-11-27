import { http } from '@/api/http'
import type { Order, Report, Schedule, WaitingPatient } from '@/types/models'

export interface ReportCreateRequest {
  orderId: number
  reportType?: string
  title: string
  content?: string
}

export const doctorWorkApi = {
  schedules() {
    return http.get<Schedule[]>('/doctor-work/schedules')
  },
  waiting(scheduleId: number) {
    return http.get<WaitingPatient[]>('/doctor-work/waiting', { params: { scheduleId } })
  },
  call(scheduleId: number) {
    return http.post<Order>('/doctor-work/call', null, { params: { scheduleId } })
  },
  complete(orderId: number) {
    return http.post<Order>('/doctor-work/complete', null, { params: { orderId } })
  },
  currentConsultation(scheduleId: number) {
    return http.get<Order | null>('/doctor-work/current', { params: { scheduleId } })
  },
  report(data: ReportCreateRequest) {
    return http.post<Report>('/doctor-work/report', data)
  }
}
