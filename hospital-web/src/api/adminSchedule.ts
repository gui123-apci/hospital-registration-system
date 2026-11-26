import { http } from '@/api/http'
import type { PageResult, Schedule } from '@/types/models'

export interface ScheduleCreateRequest {
  doctorId: number
  workDate: string
  timeSlot: 1 | 2 | 3
  totalNum: number
  price: number
  status: 0 | 1
}

export interface ScheduleUpdateRequest {
  id: number
  workDate?: string
  timeSlot: 1 | 2 | 3
  totalNum: number
  price: number
  status: 0 | 1
}

export const adminScheduleApi = {
  page(pageNum = 1, pageSize = 10, doctorId?: number) {
    return http.get<PageResult<Schedule>>('/admin/schedule/page', {
      params: { pageNum, pageSize, ...(doctorId === undefined ? {} : { doctorId }) }
    })
  },
  create(data: ScheduleCreateRequest) {
    return http.post<Schedule>('/admin/schedule', data)
  },
  update(data: ScheduleUpdateRequest) {
    return http.put<Schedule>('/admin/schedule', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/schedule/${id}`)
  }
}
