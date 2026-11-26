import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage, ElRadioGroup } from 'element-plus'
import OrderListView from '@/views/patient/OrderListView.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import type { Order } from '@/types/models'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

vi.mock('@/api/order', () => ({ orderApi: { list: vi.fn(), sign: vi.fn() } }))
vi.mock('@/api/payment', () => ({ paymentApi: { refund: vi.fn() } }))

const pending: Order = {
  id: 10,
  orderNo: 'ORDER-10',
  patientId: 17,
  patientName: '李患者',
  scheduleId: 41,
  workDate: '2026-11-25',
  timeSlot: 1,
  amount: 36,
  status: 0,
  statusDesc: '待支付',
  deadlineTime: '2026-11-25T10:00:00',
  createTime: '2026-11-25T08:55:00'
}
const paid: Order = {
  ...pending,
  id: 11,
  orderNo: 'ORDER-11',
  status: 1,
  statusDesc: '已支付'
}
const afternoon: Order = {
  ...paid,
  id: 12,
  orderNo: 'ORDER-12',
  timeSlot: 2
}
const refunded: Order = { ...paid, status: 2, statusDesc: '已退号' }

function mountOrders() {
  return mount(OrderListView, {
    global: {
      stubs: {
        teleport: true,
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }
      }
    }
  })
}

async function mountLoadedOrders() {
  const wrapper = mountOrders()
  await flushPromises()
  return wrapper
}

function buttonWithText(wrapper: Awaited<ReturnType<typeof mountLoadedOrders>>, text: string) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

async function confirmRefund(wrapper: Awaited<ReturnType<typeof mountLoadedOrders>>) {
  await buttonWithText(wrapper, '退号')!.trigger('click')
  wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
  await flushPromises()
}

describe('OrderListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:30:00'))
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [pending] })
    vi.mocked(orderApi.sign).mockResolvedValue(undefined)
    vi.mocked(paymentApi.refund).mockResolvedValue(undefined)
  })

  afterEach(() => vi.useRealTimers())

  it('loads the requested page and renders complete order details', async () => {
    const wrapper = await mountLoadedOrders()

    expect(orderApi.list).toHaveBeenCalledWith(1, 10, undefined)
    expect(wrapper.text()).toContain('ORDER-10')
    expect(wrapper.text()).toContain('李患者')
    expect(wrapper.text()).toContain('2026-11-25')
    expect(wrapper.text()).toContain('上午')
    expect(wrapper.text()).toContain('¥36.00')
    expect(wrapper.text()).toContain('待支付')
    expect(wrapper.text()).toContain('2026-11-25 10:00')
  })

  it('loads another page and disables pagination at the last page', async () => {
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 11, records: [pending] })
      .mockResolvedValueOnce({ total: 11, records: [refunded] })
    const wrapper = await mountLoadedOrders()

    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeDefined()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()

    expect(orderApi.list).toHaveBeenNthCalledWith(2, 2, 10, undefined)
    expect(wrapper.text()).toContain('ORDER-11')
    expect(wrapper.text()).toContain('已退号')
    expect(buttonWithText(wrapper, '下一页')!.attributes('disabled')).toBeDefined()
    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeUndefined()
  })

  it('shows only the actions currently allowed for each order', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      total: 4,
      records: [pending, paid, afternoon, refunded]
    })
    const wrapper = await mountLoadedOrders()
    const cards = wrapper.findAll('article')

    expect(cards).toHaveLength(4)
    expect(cards[0].text()).toContain('去支付')
    expect(cards[1].text()).toContain('签到')
    expect(cards[1].text()).not.toContain('退号')
    expect(cards[2].text()).toContain('退号')
    expect(cards[2].text()).toContain('签到')
    expect(buttonWithText(wrapper, '退号')).toBeDefined()
    expect(cards[3].find('button').exists()).toBe(false)
    expect(cards[3].find('a').exists()).toBe(false)
  })

  it('requires confirmation, refunds the selected order, and refreshes the list', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 1, records: [paid] })
      .mockResolvedValueOnce({ total: 1, records: [refunded] })
    const wrapper = await mountLoadedOrders()

    await buttonWithText(wrapper, '退号')!.trigger('click')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(true)
    expect(paymentApi.refund).not.toHaveBeenCalled()

    wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
    await flushPromises()

    expect(paymentApi.refund).toHaveBeenCalledWith(11)
    expect(orderApi.list).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('退号成功')
    expect(wrapper.text()).toContain('已退号')
    expect(buttonWithText(wrapper, '退号')).toBeUndefined()
  })

  it('signs the selected order and refreshes the list', async () => {
    const visited: Order = { ...paid, status: 3, statusDesc: '已就诊' }
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 1, records: [paid] })
      .mockResolvedValueOnce({ total: 1, records: [visited] })
    const wrapper = await mountLoadedOrders()

    await buttonWithText(wrapper, '签到')!.trigger('click')
    await flushPromises()

    expect(orderApi.sign).toHaveBeenCalledWith(11)
    expect(orderApi.list).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('签到成功')
    expect(wrapper.text()).toContain('已就诊')
    expect(buttonWithText(wrapper, '签到')).toBeUndefined()
  })

  it('hides the sign action once the order already has a sign time', async () => {
    const signed: Order = { ...paid, signTime: '2026-11-25T08:10:00' }
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [signed] })
    const wrapper = await mountLoadedOrders()

    expect(wrapper.text()).toContain('已支付')
    expect(buttonWithText(wrapper, '签到')).toBeUndefined()
  })

  it('displays backend refund errors directly without refreshing a stale success state', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [paid] })
    vi.mocked(paymentApi.refund).mockRejectedValueOnce(new Error('该订单无法退号'))
    const wrapper = await mountLoadedOrders()

    await confirmRefund(wrapper)

    expect(wrapper.get('.el-alert--error').text()).toContain('该订单无法退号')
    expect(orderApi.list).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('已支付')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
  })

  it('does not submit the same refund twice while it is pending', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [paid] })
    let finishRefund!: () => void
    vi.mocked(paymentApi.refund).mockReturnValue(new Promise<void>((resolve) => {
      finishRefund = resolve
    }))
    const wrapper = await mountLoadedOrders()
    await buttonWithText(wrapper, '退号')!.trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)

    dialog.vm.$emit('confirm')
    dialog.vm.$emit('confirm')
    expect(paymentApi.refund).toHaveBeenCalledTimes(1)

    finishRefund()
    await flushPromises()
  })

  it('preserves refund success when refreshing the list fails', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 1, records: [paid] })
      .mockRejectedValueOnce(new Error('订单刷新失败'))
    const wrapper = await mountLoadedOrders()

    await confirmRefund(wrapper)

    expect(paymentApi.refund).toHaveBeenCalledWith(11)
    expect(ElMessage.success).toHaveBeenCalledWith('退号成功')
    expect(wrapper.get('.el-alert--success').text()).toContain('退号成功')
    expect(wrapper.get('.el-alert--error').text()).toContain('订单刷新失败')
    expect(wrapper.get('.el-alert--error').text()).not.toContain('退号失败')
  })

  it('preserves sign success when refreshing the list fails', async () => {
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 1, records: [paid] })
      .mockRejectedValueOnce(new Error('订单刷新失败'))
    const wrapper = await mountLoadedOrders()

    await buttonWithText(wrapper, '签到')!.trigger('click')
    await flushPromises()

    expect(orderApi.sign).toHaveBeenCalledWith(11)
    expect(ElMessage.success).toHaveBeenCalledWith('签到成功')
    expect(wrapper.get('.el-alert--success').text()).toContain('签到成功')
    expect(wrapper.get('.el-alert--error').text()).toContain('订单刷新失败')
    expect(wrapper.get('.el-alert--error').text()).not.toContain('签到失败')
  })

  it('rechecks the refund cutoff when the confirmation dialog is submitted', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:59:59'))
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [paid] })
    const wrapper = await mountLoadedOrders()
    await buttonWithText(wrapper, '退号')!.trigger('click')

    vi.setSystemTime(new Date('2026-11-25T08:00:00'))
    wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
    await flushPromises()

    expect(paymentApi.refund).not.toHaveBeenCalled()
    expect(wrapper.get('.el-alert--error').text()).toContain('已超过退号时间')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(orderApi.list).toHaveBeenCalledTimes(1)
  })

  it('rechecks the sign window immediately before submitting', async () => {
    vi.setSystemTime(new Date('2026-11-25T11:59:59'))
    vi.mocked(orderApi.list).mockResolvedValue({ total: 1, records: [paid] })
    const wrapper = await mountLoadedOrders()

    vi.setSystemTime(new Date('2026-11-25T12:00:00'))
    await buttonWithText(wrapper, '签到')!.trigger('click')
    await flushPromises()

    expect(orderApi.sign).not.toHaveBeenCalled()
    expect(wrapper.get('.el-alert--error').text()).toContain('当前不在签到时间内')
    expect(orderApi.list).toHaveBeenCalledTimes(1)
  })

  it('clears a refresh error after a successful retry while preserving mutation success', async () => {
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(orderApi.list)
      .mockResolvedValueOnce({ total: 1, records: [paid] })
      .mockRejectedValueOnce(new Error('订单刷新失败'))
      .mockResolvedValueOnce({ total: 1, records: [refunded] })
    const wrapper = await mountLoadedOrders()
    await confirmRefund(wrapper)
    expect(wrapper.get('.el-alert--error').text()).toContain('列表刷新失败')

    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.el-alert--error').exists()).toBe(false)
    expect(wrapper.get('.el-alert--success').text()).toContain('退号成功')
    expect(wrapper.text()).toContain('已退号')
    expect(orderApi.list).toHaveBeenCalledTimes(3)
  })

  it('shows load errors with retry and an empty state when there are no orders', async () => {
    vi.mocked(orderApi.list)
      .mockRejectedValueOnce(new Error('订单加载失败'))
      .mockResolvedValueOnce({ total: 0, records: [] })
    const wrapper = await mountLoadedOrders()

    expect(wrapper.text()).toContain('订单加载失败')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(orderApi.list).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('暂无订单')
  })

  it('sends the selected status to the list query', async () => {
    const wrapper = await mountLoadedOrders()

    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 1)
    await flushPromises()

    expect(orderApi.list).toHaveBeenLastCalledWith(1, 10, 1)
  })

  it('returns to the first page when the status filter changes', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({ total: 30, records: [pending] })
    const wrapper = await mountLoadedOrders()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(orderApi.list).toHaveBeenLastCalledWith(2, 10, undefined)

    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 1)
    await flushPromises()

    expect(orderApi.list).toHaveBeenLastCalledWith(1, 10, 1)
  })
})
