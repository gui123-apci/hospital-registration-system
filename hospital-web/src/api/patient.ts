import { http } from '@/api/http'
import type { Patient } from '@/types/models'

export interface PatientCreateRequest {
  name: string
  idCard?: string
  relation: number
  phone?: string
}

export interface PatientUpdateRequest extends PatientCreateRequest {
  id: number
}

export const patientApi = {
  list() {
    return http.get<Patient[]>('/patient/list')
  },
  create(data: PatientCreateRequest) {
    return http.post<Patient>('/patient', data)
  },
  update(data: PatientUpdateRequest) {
    return http.put<Patient>('/patient', data)
  },
  remove(id: number) {
    return http.delete<void>(`/patient/${id}`)
  }
}
