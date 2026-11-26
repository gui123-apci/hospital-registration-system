import { http } from '@/api/http'
import type { Doctor } from '@/types/models'

export const doctorApi = {
  list(departmentId: number) {
    return http.get<Doctor[]>('/doctor/list', { params: { departmentId } })
  }
}
