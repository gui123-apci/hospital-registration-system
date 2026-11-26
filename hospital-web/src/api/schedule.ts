import { http } from '@/api/http'
import type { Schedule } from '@/types/models'

export const scheduleApi = {
  list(doctorId: number, days = 7) {
    return http.get<Schedule[]>('/schedule/list', { params: { doctorId, days } })
  }
}
