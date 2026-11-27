import { http } from '@/api/http'

export interface DashboardData {
  todayOrderCount: number
  todayRevenue: number
  statusDistribution: Record<number, number>
  departmentDistribution: Record<string, number>
  weeklyTrend: Record<string, number>
  weeklyRevenue: Record<string, number>
  totalPatients: number
  totalDoctors: number
  totalDepartments: number
}

export const dashboardApi = {
  overview() {
    return http.get<DashboardData>('/admin/dashboard')
  }
}
