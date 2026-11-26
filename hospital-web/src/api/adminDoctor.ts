import { http } from '@/api/http'
import type { Doctor, PageResult } from '@/types/models'

export interface DoctorCreateRequest {
  departmentId: number
  name: string
  title?: string
  intro?: string
}

export interface DoctorUpdateRequest extends DoctorCreateRequest {
  id: number
}

export const adminDoctorApi = {
  page(pageNum = 1, pageSize = 10) {
    return http.get<PageResult<Doctor>>('/admin/doctor/page', { params: { pageNum, pageSize } })
  },
  list() {
    return http.get<Doctor[]>('/admin/doctor/list')
  },
  create(data: DoctorCreateRequest) {
    return http.post<Doctor>('/admin/doctor', data)
  },
  update(data: DoctorUpdateRequest) {
    return http.put<Doctor>('/admin/doctor', data)
  },
  remove(id: number) {
    return http.delete<void>(`/admin/doctor/${id}`)
  }
}
