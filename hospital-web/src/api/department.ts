import { http } from '@/api/http'
import type { Department } from '@/types/models'

export const departmentApi = {
  list() {
    return http.get<Department[]>('/department/list')
  }
}
