import { http } from '@/api/http'

export interface NotificationItem {
  id: number
  type: number
  title: string
  content: string
  /** 0 未读 1 已读 */
  readFlag: number
  createTime: string
}

export const notificationApi = {
  unreadCount() {
    return http.get<number>('/notification/unread-count')
  },

  list(limit = 20) {
    return http.get<NotificationItem[]>('/notification/list', { params: { limit } })
  },

  markRead(id: number) {
    return http.post<void>(`/notification/${id}/read`)
  },

  markAllRead() {
    return http.post<void>('/notification/read-all')
  }
}
