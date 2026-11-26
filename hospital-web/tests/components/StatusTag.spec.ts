import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusTag from '@/components/StatusTag.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'

describe('status tags', () => {
  it('renders the supplied status text', () => {
    const wrapper = mount(StatusTag, { props: { type: 'success', text: '已支付' } })
    expect(wrapper.text()).toContain('已支付')
  })

  it.each([
    [0, '待支付'],
    [1, '已支付'],
    [2, '已退号'],
    [3, '已就诊'],
    [4, '已取消'],
    [5, '爽约']
  ] as const)('maps backend order status %i to %s', (status, text) => {
    const wrapper = mount(OrderStatusTag, { props: { status } })
    expect(wrapper.text()).toContain(text)
  })
})
