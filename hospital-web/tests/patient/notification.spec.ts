import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationBell from '@/components/NotificationBell.vue'
import { notificationApi } from '@/api/notification'

vi.mock('@/api/notification', () => ({
  notificationApi: {
    unreadCount: vi.fn(),
    list: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn()
  }
}))

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(notificationApi.unreadCount).mockResolvedValue(2)
    vi.mocked(notificationApi.list).mockResolvedValue([
      { id: 1, type: 1, title: '支付成功', content: '挂号费已支付', readFlag: 0, createTime: '2026-11-27T10:00:00' },
      { id: 2, type: 2, title: '医生已叫号', content: '请到内科就诊', readFlag: 1, createTime: '2026-11-27T11:00:00' }
    ])
    vi.mocked(notificationApi.markRead).mockResolvedValue(undefined)
    vi.mocked(notificationApi.markAllRead).mockResolvedValue(undefined)
  })

  it('shows the unread badge and decrements after reading one item', async () => {
    const wrapper = mount(NotificationBell)
    await flushPromises()
    expect(wrapper.get('.bell-badge').text()).toBe('2')

    await wrapper.get('.bell-button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('挂号费已支付')

    // 点未读的那条：调用标记已读，角标减一
    await wrapper.findAll('.notification-item')[0].trigger('click')
    await flushPromises()
    expect(notificationApi.markRead).toHaveBeenCalledWith(1)
    expect(wrapper.get('.bell-badge').text()).toBe('1')

    wrapper.unmount()
  })

  it('marks all as read and hides the badge', async () => {
    const wrapper = mount(NotificationBell)
    await flushPromises()
    await wrapper.get('.bell-button').trigger('click')
    await flushPromises()

    await wrapper.get('.read-all').trigger('click')
    await flushPromises()

    expect(notificationApi.markAllRead).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.bell-badge').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows an empty hint when there are no notifications', async () => {
    vi.mocked(notificationApi.unreadCount).mockResolvedValue(0)
    vi.mocked(notificationApi.list).mockResolvedValue([])

    const wrapper = mount(NotificationBell)
    await flushPromises()
    expect(wrapper.find('.bell-badge').exists()).toBe(false)

    await wrapper.get('.bell-button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无通知')

    wrapper.unmount()
  })
})
