import { http } from '@/api/http'
import type { Department, PageResult } from '@/types/models'

export interface DepartmentCreateRequest {
  name: string
  description?: string
}

export interface DepartmentUpdateRequest extends DepartmentCreateRequest {
  id: number
}

export const adminDepartmentApi = {
  page(pageNum = 1, pageSize = 10) {
    return http.get<PageResult<Department>>('/admin/department/page', { params: { pageNum, pageSize } })
  },
  list() {
    return http.get<Department[]>('/admin/department/list')
  },
  create(data: DepartmentCreateRequest) {
    return http.post<Department>('/admin/department', data)
  },
  update(data: DepartmentUpdateRequest) {
    return http.put<Department>('/admin/department', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/department/${id}`)
  }
}
