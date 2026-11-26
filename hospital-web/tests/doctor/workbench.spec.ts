import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick, ref } from 'vue'
import WorkbenchView from '@/views/doctor/WorkbenchView.vue'
import ReportDrawer from '@/components/ReportDrawer.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import FormDrawer from '@/components/FormDrawer.vue'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Order, Report, Schedule, WaitingPatient } from '@/types/models'

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return {
    ...actual,
    http: { get: vi.fn(), post: vi.fn() }
  }
})

const schedules: Schedule[] = [
  { id: 101, doctorId: 1, departmentId: 2, workDate: '2026-11-25', timeSlot: 1, totalNum: 20, leftNum: 18, price: 50, status: 1 },
  { id: 102, doctorId: 1, departmentId: 2, workDate: '2026-11-25', timeSlot: 2, totalNum: 20, leftNum: 20, price: 50, status: 1 },
  { id: 103, doctorId: 1, departmentId: 2, workDate: '2026-11-26', timeSlot: 1, totalNum: 20, leftNum: 20, price: 50, status: 1 }
]

const waiting: WaitingPatient[] = [
  { seq: 8, orderId: 77, orderNo: 'QUEUE-008', patientName: '王患者', signTime: '2026-11-25 08:03:00' },
  { seq: 3, orderId: 66, orderNo: 'QUEUE-003', patientName: '李患者', signTime: '2026-11-25 08:01:00' }
]

const calledOrder: Order = {
  id: 66,
  orderNo: 'QUEUE-003',
  patientId: 16,
  patientName: '李患者',
  scheduleId: 101,
  workDate: '2026-11-25',
  timeSlot: 1,
  amount: 36,
  status: 3,
  statusDesc: '已就诊',
  createTime: '2026-11-25 07:50:00'
}

const savedReport: Report = {
  id: 901,
  patientId: 16,
  patientName: '李患者',
  orderId: 66,
  reportType: '门诊报告',
  title: '呼吸科就诊记录',
  content: '按时服药',
  reportTime: '2026-11-25 09:00:00'
}

const consultationStorageKey = 'doctor-work:active-consultation'

type WaitingResult = WaitingPatient[] | Error | Promise<WaitingPatient[]>

/** 排班接口的返回；测试里按需改成错误或指定列表 */
let schedulesResult: Schedule[] | Error
/** 候诊队列接口按顺序消费；消费完后回落到默认的 waiting */
let waitingResults: WaitingResult[]

function storedConsultation(userId = 9, scheduleId = 101) {
  return { userId, scheduleId, order: calledOrder }
}

const SelectStub = defineComponent({
  props: {
    modelValue: { type: Number, default: undefined },
    options: { type: Array as () => { label: string; value: number }[], default: () => [] },
    disabled: Boolean,
    placeholder: String
  },
  emits: ['update:modelValue'],
  template: `
    <select
      :value="modelValue"
      :disabled="disabled"
      :aria-label="placeholder"
      @change="$emit('update:modelValue', Number($event.target.value))"
    >
      <option disabled value="">{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
  `
})

function mountWorkbench() {
  return mount(WorkbenchView, {
    global: { stubs: { ElSelect: SelectStub, teleport: true } }
  })
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

function getCalls(url: string) {
  return vi.mocked(http.get).mock.calls.filter(([requestUrl]) => requestUrl === url)
}

async function chooseSchedule(wrapper: ReturnType<typeof mountWorkbench>, scheduleId = 101) {
  wrapper.findComponent(NameIdSelect).vm.$emit('update:modelValue', scheduleId)
  await nextTick()
  await flushPromises()
}

function buttonWithText(wrapper: ReturnType<typeof mountWorkbench>, text: string) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

async function callNext(wrapper: ReturnType<typeof mountWorkbench>) {
  await buttonWithText(wrapper, '叫下一位')!.trigger('click')
  await flushPromises()
}

async function fillReport(wrapper: ReturnType<typeof mountWorkbench>) {
  await buttonWithText(wrapper, '填写报告')!.trigger('click')
  await nextTick()
  const drawer = wrapper.findComponent(ReportDrawer)
  await drawer.get('input[aria-label="报告类型"]').setValue('门诊报告')
  await drawer.get('input[aria-label="报告标题"]').setValue('  呼吸科就诊记录  ')
  await drawer.get('textarea[aria-label="报告内容"]').setValue('按时服药')
  return drawer
}

afterEach(() => {
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('WorkbenchView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().user = { id: 9, username: '测试医生', role: 2, roleName: '医生' }
    vi.clearAllMocks()
    schedulesResult = schedules
    waitingResults = []
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/doctor-work/schedules') {
        if (schedulesResult instanceof Error) throw schedulesResult
        return schedulesResult
      }
      if (url === '/doctor-work/waiting') {
        const next = waitingResults.shift()
        if (next === undefined) return waiting
        if (next instanceof Error) throw next
        return next
      }
      throw new Error(`unexpected GET ${url}`)
    })
    vi.mocked(http.post).mockImplementation(async (url) => {
      if (url === '/doctor-work/call') return calledOrder
      if (url === '/doctor-work/report') return savedReport
      throw new Error('unexpected endpoint')
    })
  })

  it('loads the doctor schedules from the server with readable labels', async () => {
    const wrapper = mountWorkbench()
    await settle()

    expect(getCalls('/doctor-work/schedules')).toHaveLength(1)
    expect(wrapper.findComponent(NameIdSelect).props('options')).toEqual([
      { label: '11月25日 · 上午 · 08:00—12:00', value: 101 },
      { label: '11月25日 · 下午 · 13:00—17:00', value: 102 },
      { label: '11月26日 · 上午 · 08:00—12:00', value: 103 }
    ])
    expect(wrapper.text()).not.toContain('演示排班数据')
  })

  it('shows a schedule load error and retries the request', async () => {
    schedulesResult = new Error('排班加载失败')
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.get('.schedule-notice').text()).toContain('排班加载失败')
    expect(wrapper.findComponent(NameIdSelect).props('options')).toEqual([])

    schedulesResult = schedules
    await buttonWithText(wrapper, '重新加载')!.trigger('click')
    await settle()

    expect(getCalls('/doctor-work/schedules')).toHaveLength(2)
    expect(wrapper.find('.schedule-notice').exists()).toBe(false)
    expect(wrapper.findComponent(NameIdSelect).props('options')).toHaveLength(3)
  })

  it('tells the doctor when there is no usable schedule', async () => {
    schedulesResult = []
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.text()).toContain('当前没有可出诊的排班')
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeDefined()
  })

  it('restores the active consultation and waiting queue after a refresh', async () => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify(storedConsultation()))
    const wrapper = mountWorkbench()
    await settle()

    expect(getCalls('/doctor-work/waiting')).toEqual([['/doctor-work/waiting', { params: { scheduleId: 101 } }]])
    expect(wrapper.get('.active-patient').text()).toBe('李患者')
    expect(wrapper.get('.active-panel').text()).toContain('QUEUE-003')
    expect(wrapper.findAll('.queue-row')).toHaveLength(2)
    expect(wrapper.findComponent(NameIdSelect).props('modelValue')).toBe(101)
    expect(wrapper.findComponent(NameIdSelect).props('disabled')).toBe(true)
    expect(wrapper.findComponent(ReportDrawer).props('orderId')).toBe(66)
    await buttonWithText(wrapper, '填写报告')!.trigger('click')
    expect(wrapper.findComponent(ReportDrawer).props('modelValue')).toBe(true)
    expect(http.post).not.toHaveBeenCalled()
  })

  it('removes stored context belonging to another authenticated user', async () => {
    useAuthStore().user = { id: 10, username: '另一位医生', role: 2, roleName: '医生' }
    sessionStorage.setItem(consultationStorageKey, JSON.stringify(storedConsultation()))
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('removes stored context when no authenticated user is available', async () => {
    useAuthStore().user = null
    sessionStorage.setItem(consultationStorageKey, JSON.stringify({
      scheduleId: 101,
      order: calledOrder
    }))
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('removes stored context for a schedule the doctor no longer owns', async () => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify(storedConsultation(9, 999)))
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('removes malformed stored JSON without interrupting the workbench', async () => {
    sessionStorage.setItem(consultationStorageKey, '{not-json')
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.text()).toContain('请先选择出诊排班')
    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('removes an empty stored value', async () => {
    sessionStorage.setItem(consultationStorageKey, '')
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it.each([0, 1, 2, 4, 5])('removes stored context when the order status is %s', async (status) => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify({
      ...storedConsultation(),
      order: { ...calledOrder, status }
    }))
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('restores only the allowlisted order fields', async () => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify({
      ...storedConsultation(),
      order: { ...calledOrder, unexpected: 'ignore-me' }
    }))
    const wrapper = mountWorkbench()
    await settle()

    const activeOrder = (wrapper.vm as unknown as { activeOrder: Order }).activeOrder
    expect(activeOrder).toEqual(calledOrder)
    expect(activeOrder).not.toHaveProperty('unexpected')
  })

  it('removes stored context with an invalid order shape', async () => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify({
      userId: 9,
      scheduleId: 101,
      order: { patientName: '李患者' }
    }))
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
  })

  it('shows guidance without requesting the queue when no schedule is selected', async () => {
    const wrapper = mountWorkbench()
    await settle()

    expect(wrapper.text()).toContain('请选择排班')
    expect(wrapper.findComponent(NameIdSelect).props('options').map((option) => option.value)).toEqual([101, 102, 103])
    expect(getCalls('/doctor-work/waiting')).toHaveLength(0)
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeDefined()
  })

  it('requests the selected numeric schedule id and preserves backend queue order and fields', async () => {
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)

    expect(getCalls('/doctor-work/waiting')).toEqual([['/doctor-work/waiting', { params: { scheduleId: 101 } }]])
    const rows = wrapper.findAll('.queue-row')
    expect(rows).toHaveLength(2)
    expect(rows.map((row) => row.get('.queue-seq').text())).toEqual(['8', '3'])
    expect(rows[0].text()).toContain('王患者')
    expect(rows[0].text()).toContain('QUEUE-008')
    expect(rows[0].text()).toContain('2026-11-25 08:03:00')
    expect(rows[1].text()).toContain('李患者')
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeUndefined()
  })

  it('calls with only a numeric schedule id, latches the returned order, and refreshes the queue', async () => {
    waitingResults = [waiting, [waiting[0]]]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)

    expect(http.post).toHaveBeenCalledWith('/doctor-work/call', null, { params: { scheduleId: 101 } })
    expect(getCalls('/doctor-work/waiting')).toHaveLength(2)
    expect(getCalls('/doctor-work/waiting')[1]).toEqual(['/doctor-work/waiting', { params: { scheduleId: 101 } }])
    expect(wrapper.get('.active-panel').text()).toContain('李患者')
    expect(wrapper.get('.active-panel').text()).toContain('QUEUE-003')
    expect(wrapper.get('.active-panel').text()).toContain('已就诊')
    expect(JSON.parse(sessionStorage.getItem(consultationStorageKey)!)).toEqual(storedConsultation())
    expect(wrapper.findComponent(NameIdSelect).props('disabled')).toBe(true)
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.queue-row')).toHaveLength(1)
  })

  it('latches the called patient and locks schedule and further calls before queue refresh settles', async () => {
    let finishRefresh!: (value: WaitingPatient[]) => void
    waitingResults = [waiting, new Promise((resolve) => { finishRefresh = resolve })]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)

    expect(wrapper.get('.active-patient').text()).toContain('李患者')
    expect(JSON.parse(sessionStorage.getItem(consultationStorageKey)!)).toEqual(storedConsultation())
    expect(wrapper.findComponent(NameIdSelect).props('disabled')).toBe(true)
    expect(wrapper.get('.call-button').attributes('disabled')).toBeDefined()

    await wrapper.get('.call-button').trigger('click')
    wrapper.findComponent(NameIdSelect).vm.$emit('update:modelValue', 102)
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(1)
    expect(getCalls('/doctor-work/waiting')).toHaveLength(2)

    finishRefresh([waiting[0]])
    await flushPromises()
    expect(wrapper.findAll('.queue-row')).toHaveLength(1)
    expect(wrapper.get('.active-patient').text()).toContain('李患者')
  })

  it('does not overwrite an active patient with another call or schedule selection', async () => {
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)

    await buttonWithText(wrapper, '叫下一位')!.trigger('click')
    wrapper.findComponent(NameIdSelect).vm.$emit('update:modelValue', 102)
    await flushPromises()

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(getCalls('/doctor-work/waiting')).toHaveLength(2)
    expect(wrapper.get('.active-patient').text()).toContain('李患者')
  })

  it('keeps a successful call active when only the queue refresh fails', async () => {
    waitingResults = [waiting, new Error('候诊队列暂时不可用')]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)

    expect(wrapper.get('.active-patient').text()).toContain('李患者')
    expect(wrapper.get('.queue-warning').text()).toContain('叫号成功')
    expect(wrapper.get('.queue-warning').text()).toContain('候诊队列暂时不可用')
    expect(wrapper.text()).not.toContain('叫号失败')
  })

  it('shows a direct call error and allows another attempt', async () => {
    vi.mocked(http.post)
      .mockRejectedValueOnce(new Error('当前排班不可叫号'))
      .mockResolvedValueOnce(calledOrder)
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)

    expect(wrapper.get('.call-error').text()).toContain('当前排班不可叫号')
    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeUndefined()

    await callNext(wrapper)
    expect(wrapper.get('.active-patient').text()).toContain('李患者')
    expect(wrapper.find('.call-error').exists()).toBe(false)
  })

  it('disables calling for an empty queue', async () => {
    waitingResults = [[]]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)

    expect(wrapper.text()).toContain('暂无候诊患者')
    expect(buttonWithText(wrapper, '叫下一位')!.attributes('disabled')).toBeDefined()
  })

  it('shows queue errors with retry and clears them when retry succeeds', async () => {
    waitingResults = [new Error('队列加载失败'), []]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)

    expect(wrapper.get('.queue-error').text()).toContain('队列加载失败')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(getCalls('/doctor-work/waiting')).toHaveLength(2)
    expect(wrapper.find('.queue-error').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无候诊患者')
  })

  it('ignores a stale waiting response after the schedule changes', async () => {
    let resolveFirst!: (value: WaitingPatient[]) => void
    let resolveSecond!: (value: WaitingPatient[]) => void
    waitingResults = [
      new Promise((resolve) => { resolveFirst = resolve }),
      new Promise((resolve) => { resolveSecond = resolve })
    ]
    const wrapper = mountWorkbench()
    await settle()
    wrapper.findComponent(NameIdSelect).vm.$emit('update:modelValue', 101)
    await nextTick()
    wrapper.findComponent(NameIdSelect).vm.$emit('update:modelValue', 102)
    await nextTick()

    resolveSecond([{ seq: 1, orderId: 99, orderNo: 'NEW-099', patientName: '新患者', signTime: '2026-11-25 09:00:00' }])
    await flushPromises()
    expect(wrapper.text()).toContain('NEW-099')

    resolveFirst(waiting)
    await flushPromises()
    expect(wrapper.text()).toContain('NEW-099')
    expect(wrapper.text()).not.toContain('QUEUE-008')
    expect(getCalls('/doctor-work/waiting')[1]).toEqual(['/doctor-work/waiting', { params: { scheduleId: 102 } }])
  })

  it('clears a previous call error when the schedule changes', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('旧排班不可叫号'))
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)
    expect(wrapper.find('.call-error').exists()).toBe(true)

    await chooseSchedule(wrapper, 102)
    expect(wrapper.find('.call-error').exists()).toBe(false)
  })

  it('keeps the report action available after closing the drawer', async () => {
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)
    await buttonWithText(wrapper, '填写报告')!.trigger('click')
    wrapper.findComponent(ReportDrawer).vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.get('.active-patient').text()).toContain('李患者')
    expect(buttonWithText(wrapper, '填写报告')!.attributes('disabled')).toBeUndefined()
  })

  it('clears restored context after the report is saved', async () => {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify(storedConsultation()))
    const wrapper = mountWorkbench()
    await settle()
    expect(buttonWithText(wrapper, '填写报告')).toBeDefined()
    const drawer = await fillReport(wrapper)

    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
  })

  it('submits the exact report payload and clears the active patient after saving', async () => {
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)
    const drawer = await fillReport(wrapper)
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenLastCalledWith('/doctor-work/report', {
      orderId: 66,
      reportType: '门诊报告',
      title: '呼吸科就诊记录',
      content: '按时服药'
    })
    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(sessionStorage.getItem(consultationStorageKey)).toBeNull()
    expect(wrapper.findComponent(ReportDrawer).exists()).toBe(false)
    expect(wrapper.findComponent(NameIdSelect).props('disabled')).toBe(false)
    expect(getCalls('/doctor-work/waiting')).toHaveLength(3)
    expect(wrapper.text()).toContain('报告已保存')
  })

  it('clears the active patient and unlocks schedule before report refresh settles', async () => {
    let finishRefresh!: (value: WaitingPatient[]) => void
    waitingResults = [waiting, [waiting[0]], new Promise((resolve) => { finishRefresh = resolve })]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)
    const drawer = await fillReport(wrapper)
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(getCalls('/doctor-work/waiting')).toHaveLength(3)
    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(wrapper.findComponent(ReportDrawer).exists()).toBe(false)
    expect(wrapper.findComponent(NameIdSelect).props('disabled')).toBe(false)
    expect(wrapper.get('.success-alert').text()).toContain('报告已保存')

    finishRefresh([])
    await flushPromises()
    expect(wrapper.text()).toContain('暂无候诊患者')
    expect(wrapper.get('.success-alert').text()).toContain('报告已保存')
  })

  it('preserves report-save success when queue refresh fails and prevents duplicate submission', async () => {
    waitingResults = [waiting, [waiting[0]], new Error('报告后队列刷新失败')]
    const wrapper = mountWorkbench()
    await settle()
    await chooseSchedule(wrapper)
    await callNext(wrapper)
    const drawer = await fillReport(wrapper)
    const form = drawer.get('form')
    await form.trigger('submit')
    await flushPromises()
    await form.trigger('submit')
    await flushPromises()

    expect(vi.mocked(http.post).mock.calls.filter(([url]) => url === '/doctor-work/report')).toHaveLength(1)
    expect(wrapper.find('.active-patient').exists()).toBe(false)
    expect(wrapper.get('.queue-warning').text()).toContain('报告已保存')
    expect(wrapper.get('.queue-warning').text()).toContain('报告后队列刷新失败')
    expect(wrapper.text()).not.toContain('报告保存失败')
    expect(buttonWithText(wrapper, '填写报告')).toBeUndefined()
  })
})

describe('ReportDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(http.post).mockResolvedValue(savedReport)
  })

  async function mountReport() {
    const wrapper = mount(ReportDrawer, {
      props: { modelValue: true, orderId: 66, patientName: '李患者' },
      attachTo: document.body,
      global: { stubs: { teleport: true } }
    })
    await nextTick()
    await flushPromises()
    return wrapper
  }

  it('uses a viewport-capped width in the real drawer component', async () => {
    const wrapper = await mountReport()

    expect(wrapper.findComponent(FormDrawer).props('size')).toBe('min(520px, 100vw)')
    expect(wrapper.findComponent({ name: 'ElDrawer' }).props('size')).toBe('min(520px, 100vw)')
  })

  it('keeps a pending report open and locked when the real close control and reopen are used', async () => {
    vi.useFakeTimers()
    let finishReport!: (value: Report) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishReport = resolve }))
    const wrapper = mount(defineComponent({
      components: { ReportDrawer },
      setup() {
        const reportOpen = ref(true)
        return { reportOpen, reopen: () => { reportOpen.value = true }, close: () => { reportOpen.value = false } }
      },
      template: `
        <ReportDrawer v-model="reportOpen" :order-id="66" patient-name="李患者" />
        <button class="close-report" type="button" @click="close">父级关闭报告</button>
        <button class="reopen-report" type="button" @click="reopen">重新打开报告</button>
      `
    }), {
      attachTo: document.body,
      global: { stubs: { teleport: true } }
    })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(ReportDrawer)
    await drawer.get('input[aria-label="报告类型"]').setValue('门诊报告')
    await drawer.get('input[aria-label="报告标题"]').setValue('呼吸科就诊记录')
    await drawer.get('textarea[aria-label="报告内容"]').setValue('按时服药')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    const closeButton = document.querySelector<HTMLButtonElement>('.el-drawer__close-btn')
    expect(closeButton).not.toBeNull()
    closeButton!.click()
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(document.querySelector('.el-drawer')?.classList.contains('open')).toBe(true)
    expect(drawer.props('modelValue')).toBe(true)

    await wrapper.get('.close-report').trigger('click')
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(false)
    await wrapper.get('.reopen-report').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="报告标题"]').element as HTMLInputElement).value).toBe('呼吸科就诊记录')
    expect((drawer.get('textarea[aria-label="报告内容"]').element as HTMLTextAreaElement).value).toBe('按时服药')
    expect(drawer.get('input[aria-label="报告类型"]').attributes('disabled')).toBeDefined()
    expect(drawer.get('input[aria-label="报告标题"]').attributes('disabled')).toBeDefined()
    expect(drawer.get('textarea[aria-label="报告内容"]').attributes('disabled')).toBeDefined()
    expect(drawer.get('.report-actions button').attributes('disabled')).toBeDefined()
    expect(drawer.get('button[type="submit"]').text()).toContain('正在保存')

    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(http.post).mock.calls.filter(([url]) => url === '/doctor-work/report')).toHaveLength(1)

    finishReport(savedReport)
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(false)
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    await wrapper.get('.reopen-report').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="报告标题"]').element as HTMLInputElement).value).toBe('呼吸科就诊记录')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(http.post).mock.calls.filter(([url]) => url === '/doctor-work/report')).toHaveLength(1)
    wrapper.unmount()
  })

  it.each([
    ['reportType', '报'.repeat(33), '报告类型不能超过 32 个字'],
    ['title', '   ', '请输入报告标题'],
    ['title', '题'.repeat(65), '报告标题不能超过 64 个字']
  ])('rejects invalid %s before calling the API', async (field, value, message) => {
    const wrapper = await mountReport()
    const selector = field === 'reportType' ? 'input[aria-label="报告类型"]' : 'input[aria-label="报告标题"]'
    await wrapper.get(selector).setValue(value)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalled()
    const input = wrapper.get(selector)
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${input.attributes('aria-describedby')}`).text()).toContain(message)
    expect(wrapper.findComponent(FormDrawer).props('modelValue')).toBe(true)
  })

  it('sends all four keys, closes before emitting saved, and allows duplicate submission prevention', async () => {
    let finishReport!: (value: Report) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishReport = resolve }))
    const wrapper = await mountReport()
    await wrapper.get('input[aria-label="报告类型"]').setValue(' 门诊报告 ')
    await wrapper.get('input[aria-label="报告标题"]').setValue(' 呼吸科就诊记录 ')
    await wrapper.get('textarea[aria-label="报告内容"]').setValue(' 按时服药 ')
    const form = wrapper.get('form')

    await form.trigger('submit')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await form.trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/doctor-work/report', {
      orderId: 66,
      reportType: '门诊报告',
      title: '呼吸科就诊记录',
      content: '按时服药'
    })

    finishReport(savedReport)
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('saved')).toEqual([[]])
    expect(Object.keys(wrapper.emitted()).filter((key) => key === 'update:modelValue' || key === 'saved'))
      .toEqual(['update:modelValue', 'saved'])
  })

  it('keeps backend errors visible in the open drawer for correction', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('该订单不属于当前医生'))
    const wrapper = await mountReport()
    await wrapper.get('input[aria-label="报告标题"]').setValue('就诊记录')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.report-error').text()).toContain('该订单不属于当前医生')
    expect(wrapper.findComponent(FormDrawer).props('modelValue')).toBe(true)
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })
})
