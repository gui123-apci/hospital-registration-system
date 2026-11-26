import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import BookingView from '@/views/patient/BookingView.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import { doctorApi } from '@/api/doctor'
import { orderApi, type CreateOrderResult } from '@/api/order'
import { patientApi } from '@/api/patient'
import { scheduleApi } from '@/api/schedule'
import type { Doctor, Order, Patient, Schedule } from '@/types/models'
import { timeSlotLabel, scheduleLabel } from '@/utils/schedule'

const route = reactive({
  query: {
    scheduleId: '41',
    doctorId: '7',
    departmentId: '3'
  }
})
const router = { push: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

vi.mock('@/api/doctor', () => ({ doctorApi: { list: vi.fn() } }))
vi.mock('@/api/order', () => ({ orderApi: { create: vi.fn() } }))
vi.mock('@/api/patient', () => ({ patientApi: { list: vi.fn() } }))
vi.mock('@/api/schedule', () => ({ scheduleApi: { list: vi.fn() } }))

const doctor: Doctor = {
  id: 7,
  departmentId: 3,
  name: '张医生',
  title: '主任医师',
  intro: '呼吸内科专家'
}
const schedule: Schedule = {
  id: 41,
  doctorId: 7,
  departmentId: 3,
  workDate: '2026-11-26',
  timeSlot: 1,
  totalNum: 20,
  leftNum: 5,
  price: 36,
  status: 1
}
const patient: Patient = {
  id: 17,
  name: '李患者',
  relation: 1,
  phone: '13800000000'
}
const order: Order = {
  id: 99,
  orderNo: 'ORDER-99',
  patientId: 17,
  patientName: '李患者',
  scheduleId: 41,
  workDate: '2026-11-26',
  timeSlot: 1,
  amount: 36,
  status: 0,
  statusDesc: '待支付',
  createTime: '2026-11-25 10:00:00'
}
const backendMessage = '挂号成功，请在30分钟内完成支付'

async function mountLoadedBooking() {
  const wrapper = mount(BookingView, {
    global: { stubs: { RouterLink: true } }
  })
  await flushPromises()
  return wrapper
}

async function selectPatient(wrapper: Awaited<ReturnType<typeof mountLoadedBooking>>) {
  const select = wrapper.findComponent(NameIdSelect)
  select.vm.$emit('update:modelValue', patient.id)
  await nextTick()
  return select
}

describe('booking labels and payload rules', () => {
  it('uses Chinese time slot labels', () => {
    expect(timeSlotLabel(1)).toBe('上午')
    expect(timeSlotLabel(2)).toBe('下午')
    expect(timeSlotLabel(3)).toBe('晚间')
  })

  it('builds a human-readable schedule label', () => {
    expect(scheduleLabel({ workDate: '2026-11-25', timeSlot: 1 }, '张医生'))
      .toBe('2026-11-25 上午 · 张医生')
  })
})

describe('BookingView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query.scheduleId = '41'
    route.query.doctorId = '7'
    route.query.departmentId = '3'
    vi.mocked(doctorApi.list).mockResolvedValue([doctor])
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule])
    vi.mocked(patientApi.list).mockResolvedValue([patient])
    vi.mocked(orderApi.create).mockResolvedValue({ order, message: backendMessage })
    vi.mocked(router.push).mockResolvedValue(undefined)
  })

  it('loads and renders the booking context from query IDs', async () => {
    const wrapper = await mountLoadedBooking()

    expect(doctorApi.list).toHaveBeenCalledWith(3)
    expect(scheduleApi.list).toHaveBeenCalledWith(7)
    expect(patientApi.list).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.visit-details').text()).toContain('张医生')
    expect(wrapper.get('.visit-details').text()).toContain('主任医师')
    expect(wrapper.get('.visit-details').text()).toContain('2026-11-26 上午 · 张医生')
    expect(wrapper.get('.visit-details').text()).toContain('¥36.00')
    expect(wrapper.findComponent(NameIdSelect).props('options')).toEqual([
      { label: '李患者 · 关系：父母', value: 17 }
    ])
  })

  it('gives the patient selector an explicit accessible label', async () => {
    const wrapper = await mountLoadedBooking()

    expect(wrapper.get('input').attributes('aria-label')).toBe('选择就诊人')
  })

  it('submits only the schedule and patient IDs for the selected patient', async () => {
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)

    expect(wrapper.findComponent(NameIdSelect).props('modelValue')).toBe(17)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderApi.create).toHaveBeenCalledTimes(1)
    expect(orderApi.create).toHaveBeenCalledWith({ scheduleId: 41, patientId: 17 })
    expect(router.push).toHaveBeenCalledWith({ path: '/patient/payment', query: { orderId: 99 } })
  })

  it('ignores the booking response when the schedule changed mid-submit', async () => {
    let finish!: (value: CreateOrderResult) => void
    vi.mocked(orderApi.create).mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(orderApi.create).toHaveBeenCalledTimes(1)

    route.query.scheduleId = '42'
    await nextTick()
    await flushPromises()

    finish({ order, message: backendMessage })
    await flushPromises()

    expect(router.push).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(wrapper.find('.el-alert--success').exists()).toBe(false)
  })

  it('displays the backend booking success message instead of synthesizing it from order status', async () => {
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.el-alert--success').text()).toContain(backendMessage)
    expect(wrapper.text()).not.toContain('挂号成功，待支付')
    expect(ElMessage.success).toHaveBeenCalledWith(backendMessage)
  })

  it('falls back to the payment hint only when the backend message is absent', async () => {
    vi.mocked(orderApi.create).mockResolvedValue({ order, message: '' })
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.el-alert--success').text()).toContain('挂号成功，请完成支付')
  })

  it('does not create a duplicate order while the first submission is pending', async () => {
    let finishCreate!: (result: CreateOrderResult) => void
    vi.mocked(orderApi.create).mockReturnValue(new Promise((resolve) => {
      finishCreate = resolve
    }))
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)
    const form = wrapper.get('form')

    await form.trigger('submit')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await form.trigger('submit')

    expect(orderApi.create).toHaveBeenCalledTimes(1)
    finishCreate({ order, message: backendMessage })
    await flushPromises()
  })

  it.each([
    ['scheduleId', '42', 3, 7],
    ['doctorId', '8', 3, 8],
    ['departmentId', '4', 4, 7]
  ] as const)('reloads and resets booking state when only %s changes', async (key, value, departmentId, doctorId) => {
    const wrapper = await mountLoadedBooking()
    await selectPatient(wrapper)
    const nextDoctor = { ...doctor, id: doctorId, departmentId, name: doctorId === 7 ? '张医生' : '王医生' }
    const nextSchedule = {
      ...schedule,
      id: key === 'scheduleId' ? 42 : 41,
      doctorId,
      departmentId,
      workDate: '2026-11-27'
    }
    vi.mocked(doctorApi.list).mockResolvedValue([nextDoctor])
    vi.mocked(scheduleApi.list).mockResolvedValue([nextSchedule])

    route.query[key] = value
    await flushPromises()

    expect(doctorApi.list).toHaveBeenLastCalledWith(departmentId)
    expect(scheduleApi.list).toHaveBeenLastCalledWith(doctorId)
    expect(wrapper.get('.visit-details').text()).toContain(nextDoctor.name)
    expect(wrapper.get('.visit-details').text()).toContain('2026-11-27 上午')
    expect(wrapper.findComponent(NameIdSelect).props('modelValue')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
