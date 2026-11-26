export type Role = 0 | 1 | 2
export type TimeSlot = 1 | 2 | 3
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6

export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserInfo {
  id: number
  username: string
  phone?: string
  role: Role
  roleName: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface Patient {
  id: number
  name: string
  idCard?: string
  relation: number
  phone?: string
}

export interface Department {
  id: number
  name: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface Doctor {
  id: number
  departmentId: number
  name: string
  title?: string
  intro?: string
  createTime?: string
  updateTime?: string
}

export interface Schedule {
  id: number
  doctorId: number
  departmentId: number
  workDate: string
  timeSlot: TimeSlot
  totalNum: number
  leftNum: number
  price: number
  status: 0 | 1
}

export interface Order {
  id: number
  orderNo: string
  patientId: number
  patientName: string
  scheduleId: number
  workDate: string
  timeSlot: TimeSlot
  amount: number
  status: OrderStatus
  statusDesc: string
  deadlineTime?: string
  signTime?: string
  createTime: string
}

export interface Report {
  id: number
  patientId: number
  patientName: string
  orderId: number
  reportType?: string
  title: string
  content?: string
  reportTime?: string
}

export interface WaitingPatient {
  seq: number
  orderId: number
  orderNo: string
  patientName: string
  signTime: string
}

export interface TriageSchedule {
  scheduleId: number
  doctorId: number
  departmentId: number
  doctorName: string
  workDate: string
  timeSlot: TimeSlot
  leftNum: number
  price: number
}

export interface TriageResult {
  answer: string
  schedules: TriageSchedule[]
}
