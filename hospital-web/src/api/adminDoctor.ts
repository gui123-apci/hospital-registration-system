import { http } from '@/api/http'
import type { Doctor, PageResult } from '@/types/models'

export interface DoctorCreateRequest {
  departmentId: number
  name: string
  title?: string
  intro?: string
  /** 手机号同时作为医生登录账号，后端自动创建角色为医生的账号 */
  phone: string
}

export interface DoctorUpdateRequest {
  id: number
  departmentId: number
  name: string
  title?: string
  intro?: string
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
