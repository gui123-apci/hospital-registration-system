import { http } from '@/api/http'
import type { Report } from '@/types/models'

export const reportApi = {
  list() {
    return http.get<Report[]>('/report/list')
  }
}
