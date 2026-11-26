import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import PaymentView from '@/views/patient/PaymentView.vue'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import type { Order } from '@/types/models'

const route = reactive<{ query: { orderId: string } }>({ query: { orderId: '99' } })
const router = { push: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

vi.mock('@/api/order', () => ({ orderApi: { get: vi.fn() } }))
vi.mock('@/api/payment', () => ({ paymentApi: { pay: vi.fn() } }))

const order: Order = {
  id: 99,
  orderNo: 'ORDER-99',
  patientId: 17,
  patientName: '李患者',
  scheduleId: 41,
  workDate: '2026-11-25',
  timeSlot: 1,
  amount: 36,
  status: 0,
  statusDesc: '待支付',
  deadlineTime: '2026-11-25T09:05:00',
  createTime: '2026-11-25T08:55:00'
}

function mountPayment() {
  return mount(PaymentView, {
    global: {
      stubs: {
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }
      }
    }
  })
}

async function mountLoadedPayment() {
  const wrapper = mountPayment()
  await flushPromises()
  return wrapper
}

function payButton(wrapper: Awaited<ReturnType<typeof mountLoadedPayment>>) {
  return wrapper.findAll('button').find((button) => button.text() === '模拟支付')!
}

describe('PaymentView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    route.query.orderId = '99'
    vi.mocked(orderApi.get).mockResolvedValue(order)
    vi.mocked(paymentApi.pay).mockResolvedValue(undefined)
    vi.mocked(router.push).mockResolvedValue(undefined)
  })

  afterEach(() => vi.useRealTimers())

  it('looks up the query order and renders its amount and live countdown', async () => {
    const wrapper = await mountLoadedPayment()

    expect(orderApi.get).toHaveBeenCalledWith(99)
    expect(wrapper.text()).toContain('ORDER-99')
    expect(wrapper.text()).toContain('李患者')
    expect(wrapper.text()).toContain('¥36.00')
    expect(wrapper.text()).toContain('模拟支付')
    expect(wrapper.getComponent({ name: 'PaymentCountdown' }).text()).toBe('05:00')

    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(wrapper.getComponent({ name: 'PaymentCountdown' }).text()).toBe('04:59')
  })

  it('pays through channel zero, announces success, and returns to orders', async () => {
    const wrapper = await mountLoadedPayment()

    await payButton(wrapper).trigger('click')
    await flushPromises()

    expect(paymentApi.pay).toHaveBeenCalledTimes(1)
    expect(paymentApi.pay).toHaveBeenCalledWith(99, 0)
    expect(ElMessage.success).toHaveBeenCalledWith('模拟支付成功')
    expect(router.push).toHaveBeenCalledWith('/patient/orders')
  })

  it('keeps payment latched and distinguishes navigation failure after success', async () => {
    vi.mocked(router.push).mockRejectedValueOnce(new Error('导航被取消'))
    const wrapper = await mountLoadedPayment()

    await payButton(wrapper).trigger('click')
    await flushPromises()

    expect(paymentApi.pay).toHaveBeenCalledTimes(1)
    expect(ElMessage.success).toHaveBeenCalledWith('模拟支付成功')
    expect(wrapper.get('.el-alert--warning').text()).toContain('支付成功')
    expect(wrapper.get('.el-alert--warning').text()).toContain('返回订单列表失败')
    expect(wrapper.get('.pay-button').attributes('disabled')).toBeDefined()

    await wrapper.get('.pay-button').trigger('click')
    expect(paymentApi.pay).toHaveBeenCalledTimes(1)
  })

  it('ignores a delayed lookup response after orderId changes', async () => {
    const firstOrder = { ...order, id: 98, orderNo: 'ORDER-98' }
    const currentOrder = { ...order, id: 100, orderNo: 'ORDER-100' }
    let resolveFirst!: (value: Order) => void
    let resolveCurrent!: (value: Order) => void
    vi.mocked(orderApi.get)
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise((resolve) => { resolveCurrent = resolve }))
    const wrapper = mountPayment()
    await nextTick()

    route.query.orderId = '100'
    await nextTick()
    resolveCurrent(currentOrder)
    await flushPromises()
    expect(wrapper.text()).toContain('ORDER-100')

    resolveFirst(firstOrder)
    await flushPromises()
    expect(wrapper.text()).toContain('ORDER-100')
    expect(wrapper.text()).not.toContain('ORDER-98')
  })

  it('ignores payment completion from an invalidated orderId context', async () => {
    const currentOrder = { ...order, id: 100, orderNo: 'ORDER-100' }
    vi.mocked(orderApi.get)
      .mockResolvedValueOnce(order)
      .mockResolvedValueOnce(currentOrder)
    let finishPayment!: () => void
    vi.mocked(paymentApi.pay).mockReturnValueOnce(new Promise<void>((resolve) => {
      finishPayment = resolve
    }))
    const wrapper = await mountLoadedPayment()
    await payButton(wrapper).trigger('click')

    route.query.orderId = '100'
    await nextTick()
    await flushPromises()
    expect(wrapper.text()).toContain('ORDER-100')

    finishPayment()
    await flushPromises()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
    expect(payButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('prevents duplicate payment submissions while the request is pending', async () => {
    let finishPayment!: () => void
    vi.mocked(paymentApi.pay).mockReturnValue(new Promise<void>((resolve) => {
      finishPayment = resolve
    }))
    const wrapper = await mountLoadedPayment()
    const button = payButton(wrapper)

    await button.trigger('click')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(paymentApi.pay).toHaveBeenCalledTimes(1)

    finishPayment()
    await flushPromises()
  })

  it('disables payment as soon as the countdown reaches zero', async () => {
    vi.mocked(orderApi.get).mockResolvedValue({
      ...order,
      deadlineTime: '2026-11-25T09:00:02'
    })
    const wrapper = await mountLoadedPayment()
    expect(payButton(wrapper).attributes('disabled')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(2000)
    await nextTick()

    expect(wrapper.getComponent({ name: 'PaymentCountdown' }).text()).toBe('00:00')
    expect(payButton(wrapper).attributes('disabled')).toBeDefined()
    await payButton(wrapper).trigger('click')
    expect(paymentApi.pay).not.toHaveBeenCalled()
  })

  it('shows an error and a way back when the order lookup fails', async () => {
    vi.mocked(orderApi.get).mockRejectedValueOnce(new Error('订单不存在'))
    const wrapper = await mountLoadedPayment()

    expect(wrapper.text()).toContain('订单不存在')
    expect(wrapper.get('a[href="/patient/orders"]').text()).toContain('返回订单列表')
    expect(paymentApi.pay).not.toHaveBeenCalled()
  })

  it('displays backend payment errors directly and allows another attempt', async () => {
    vi.mocked(paymentApi.pay).mockRejectedValueOnce(new Error('支付通道繁忙，请稍后重试'))
    const wrapper = await mountLoadedPayment()

    await payButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('.el-alert--error').text()).toContain('支付通道繁忙，请稍后重试')
    expect(router.push).not.toHaveBeenCalled()
    expect(payButton(wrapper).attributes('disabled')).toBeUndefined()
  })
})
