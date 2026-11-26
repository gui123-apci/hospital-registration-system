import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick, ref } from 'vue'
import { ElLoading, ElMessage } from 'element-plus'
import { adminScheduleApi } from '@/api/adminSchedule'
import { http } from '@/api/http'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ErrorState from '@/components/ErrorState.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { Department, Doctor, PageResult, Schedule, TimeSlot } from '@/types/models'

vi.mock('@/api/http', () => ({
  TOKEN_KEY: 'token',
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

const pediatrics: Department = { id: 3, name: '儿科', description: '儿童诊疗' }
const internal: Department = { id: 11, name: '内科', description: '成人心血管诊疗' }
const departments = [pediatrics, internal]
const zhang: Doctor = { id: 5, departmentId: 3, name: '张医生', title: '主任医师' }
const li: Doctor = { id: 6, departmentId: 11, name: '李医生', title: '主治医师' }
const doctors = [zhang, li]
const morning: Schedule = { id: 51, doctorId: 5, departmentId: 3, workDate: '2026-11-27', timeSlot: 1, totalNum: 20, leftNum: 15, price: 30.5, status: 1 }
const afternoon: Schedule = { id: 52, doctorId: 6, departmentId: 11, workDate: '2026-11-27', timeSlot: 2, totalNum: 12, leftNum: 0, price: 45, status: 0 }

function pageResult(records: Schedule[], total = records.length): PageResult<Schedule> {
  return { total, records }
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function scheduleRows(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.el-table__body-wrapper tbody tr.el-table__row')
}

const selectStubs = {
  ElSelect: {
    props: ['modelValue', 'placeholder', 'disabled'],
    emits: ['update:modelValue'],
    template: `<select :aria-label="placeholder" :disabled="disabled" :value="modelValue" @change="$emit('update:modelValue', $event.target.value === '' ? undefined : Number($event.target.value))"><slot /></select>`
  },
  ElOption: { props: ['label', 'value'], template: `<option :value="value">{{ label }}</option>` }
}

async function drawerComponent() {
  return (await import('@/components/ScheduleFormDrawer.vue')).default
}

async function manageComponent() {
  return (await import('@/views/admin/ScheduleManageView.vue')).default
}

async function mountDrawer(schedule: Schedule | null = null) {
  const ScheduleFormDrawer = await drawerComponent()
  const wrapper = mount(ScheduleFormDrawer, {
    props: { modelValue: true, schedule },
    attachTo: document.body,
    global: { stubs: { teleport: true, ...selectStubs } }
  })
  await nextTick()
  await flushPromises()
  return wrapper
}

async function fillDrawer(drawer: ReturnType<typeof mount>, values: {
  doctorId?: number
  workDate?: string
  timeSlot?: TimeSlot
  totalNum?: string
  price?: string
  status?: 0 | 1
} = {}) {
  if (values.doctorId !== undefined) await drawer.get('select[aria-label="请选择医生"]').setValue(String(values.doctorId))
  if (values.workDate !== undefined) await drawer.get('input[aria-label="出诊日期"]').setValue(values.workDate)
  if (values.timeSlot !== undefined) await drawer.get('select[aria-label="出诊时段"]').setValue(String(values.timeSlot))
  if (values.totalNum !== undefined) await drawer.get('input[aria-label="放号数量"]').setValue(values.totalNum)
  if (values.price !== undefined) await drawer.get('input[aria-label="挂号费"]').setValue(values.price)
  if (values.status !== undefined) await drawer.get('select[aria-label="排班状态"]').setValue(String(values.status))
}

async function startCreate(wrapper: ReturnType<typeof mount>) {
  await buttonWithText(wrapper, '新增排班')!.trigger('click')
  await nextTick()
  return wrapper.findComponent({ name: 'ScheduleFormDrawer' })
}

async function startEdit(wrapper: ReturnType<typeof mount>, schedule = morning) {
  const row = scheduleRows(wrapper).find((item) => item.text().includes(String(schedule.id)))!
  await row.get(`button[aria-label="编辑排班 ${schedule.id}"]`).trigger('click')
  await nextTick()
  return wrapper.findComponent({ name: 'ScheduleFormDrawer' })
}

async function confirmDelete(wrapper: ReturnType<typeof mount>, schedule = morning) {
  const row = scheduleRows(wrapper).find((item) => item.text().includes(String(schedule.id)))!
  await row.get(`button[aria-label="删除排班 ${schedule.id}"]`).trigger('click')
  wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
  await flushPromises()
}

function setupHttp(page: PageResult<Schedule> = pageResult([morning, afternoon])) {
  vi.mocked(http.get).mockImplementation(async (url) => {
    if (url === '/admin/schedule/page') return page
    if (url === '/admin/doctor/list') return doctors
    if (url === '/admin/department/list') return departments
    throw new Error(`Unexpected GET ${url}`)
  })
  vi.mocked(http.post).mockResolvedValue(morning)
  vi.mocked(http.put).mockResolvedValue(morning)
  vi.mocked(http.delete).mockResolvedValue(undefined)
}

async function mountSchedules() {
  const ScheduleManageView = await manageComponent()
  const wrapper = mount(ScheduleManageView, {
    attachTo: document.body,
    global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } }
  })
  await flushPromises()
  return wrapper
}

describe('admin schedule api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.post).mockResolvedValue(morning)
    vi.mocked(http.put).mockResolvedValue(morning)
    vi.mocked(http.delete).mockResolvedValue(undefined)
  })

  it('creates with doctor id, numeric slot, and no left or department fields', async () => {
    const payload = { doctorId: 21, workDate: '2026-11-25', timeSlot: 1 as const, totalNum: 20, price: 30, status: 1 as const }
    await adminScheduleApi.create(payload)
    expect(http.post).toHaveBeenCalledWith('/admin/schedule', payload)
  })

  it('updates with a numeric id but never sends immutable doctorId', async () => {
    const payload = { id: 51, workDate: '2026-11-26', timeSlot: 3 as const, totalNum: 10, price: 30.25, status: 0 as const }
    await adminScheduleApi.update(payload)
    expect(http.put).toHaveBeenCalledWith('/admin/schedule', payload)
    expect(vi.mocked(http.put).mock.calls[0][1]).not.toHaveProperty('doctorId')
  })

  it('pages with defaults, custom size, and an optional numeric doctor filter', async () => {
    await adminScheduleApi.page()
    await adminScheduleApi.page(4, 25)
    await adminScheduleApi.page(2, 10, 5)
    expect(http.get).toHaveBeenNthCalledWith(1, '/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/admin/schedule/page', { params: { pageNum: 4, pageSize: 25 } })
    expect(http.get).toHaveBeenNthCalledWith(3, '/admin/schedule/page', { params: { pageNum: 2, pageSize: 10, doctorId: 5 } })
  })

  it('deletes the exact numeric schedule id', async () => {
    await adminScheduleApi.remove(51)
    expect(http.delete).toHaveBeenCalledWith('/admin/schedule/51')
  })
})

describe('ScheduleFormDrawer', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T07:30:00'))
    vi.mocked(http.get).mockResolvedValue(doctors)
    vi.mocked(http.post).mockResolvedValue(morning)
    vi.mocked(http.put).mockResolvedValue(morning)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('loads doctor names and numeric ids, blocks past dates, and creates a numeric payload', async () => {
    const wrapper = await mountDrawer()
    const select = wrapper.findComponent(NameIdSelect)
    expect(select.props('options')).toEqual([{ label: '张医生', value: 5 }, { label: '李医生', value: 6 }])
    expect(http.get).toHaveBeenCalledWith('/admin/doctor/list')
    expect(wrapper.get('input[aria-label="出诊日期"]').attributes('min')).toBe('2026-11-25')
    expect(wrapper.get('select[aria-label="出诊时段"]').findAll('option').map((option) => option.text())).toEqual(['上午', '下午', '晚间'])
    expect(wrapper.get('select[aria-label="排班状态"]').findAll('option').map((option) => option.text())).toEqual(['可预约', '停诊'])

    await fillDrawer(wrapper, { doctorId: 5, workDate: '2026-11-27', timeSlot: 2, totalNum: '20', price: '30.25', status: 1 })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/schedule', { doctorId: 5, workDate: '2026-11-27', timeSlot: 2, totalNum: 20, price: 30.25, status: 1 })
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('saved')).toEqual([[morning]])
  })

  it('prefills every edit field, locks the doctor, and omits doctorId on update', async () => {
    const wrapper = await mountDrawer(morning)
    expect((wrapper.get('select[aria-label="请选择医生"]').element as HTMLSelectElement).value).toBe('5')
    expect(wrapper.get('select[aria-label="请选择医生"]').attributes('disabled')).toBeDefined()
    expect((wrapper.get('input[aria-label="出诊日期"]').element as HTMLInputElement).value).toBe('2026-11-27')
    expect((wrapper.get('select[aria-label="出诊时段"]').element as HTMLSelectElement).value).toBe('1')
    expect((wrapper.get('input[aria-label="放号数量"]').element as HTMLInputElement).value).toBe('20')
    expect((wrapper.get('input[aria-label="挂号费"]').element as HTMLInputElement).value).toBe('30.5')
    expect((wrapper.get('select[aria-label="排班状态"]').element as HTMLSelectElement).value).toBe('1')

    await fillDrawer(wrapper, { workDate: '2026-11-28', timeSlot: 3, totalNum: '25', price: '31.20', status: 0 })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/schedule', { id: 51, workDate: '2026-11-28', timeSlot: 3, totalNum: 25, price: 31.2, status: 0 })
    expect(http.post).not.toHaveBeenCalled()
  })

  it.each([
    ['doctorId', '', '请选择医生'],
    ['workDate', '', '请选择出诊日期'],
    ['timeSlot', '', '请选择出诊时段'],
    ['totalNum', '', '请输入放号数量'],
    ['totalNum', '0', '放号数量必须为大于等于 1 的整数'],
    ['totalNum', '1.5', '放号数量必须为大于等于 1 的整数'],
    ['price', '', '请输入挂号费'],
    ['price', '-0.01', '挂号费必须在 0 到 99999999.99 之间'],
    ['price', '100000000', '挂号费必须在 0 到 99999999.99 之间'],
    ['price', '1.001', '挂号费最多保留两位小数']
  ])('rejects invalid %s=%s with an accessible error', async (field, value, message) => {
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: '20', price: '30', [field]: value })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const selector = field === 'doctorId' ? 'select[aria-label="请选择医生"]'
      : field === 'workDate' ? 'input[aria-label="出诊日期"]'
        : field === 'timeSlot' ? 'select[aria-label="出诊时段"]'
          : field === 'totalNum' ? 'input[aria-label="放号数量"]' : 'input[aria-label="挂号费"]'
    const input = wrapper.get(selector)
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${input.attributes('aria-describedby')}`).text()).toContain(message)
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('accepts the inclusive price bounds and zero price', async () => {
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: '1', price: '0' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenLastCalledWith('/admin/schedule', { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: 1, price: 0, status: 1 })
    wrapper.unmount()

    const upper = await mountDrawer()
    await fillDrawer(upper, { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: '1', price: '99999999.99' })
    await upper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenLastCalledWith('/admin/schedule', { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: 1, price: 99999999.99, status: 1 })
  })

  it('rejects a past date and a today slot at its exact start boundary on create', async () => {
    const past = await mountDrawer()
    await fillDrawer(past, { doctorId: 5, workDate: '2026-11-24', timeSlot: 3, totalNum: '1', price: '0' })
    await past.get('form').trigger('submit')
    await flushPromises()
    expect(past.get('#schedule-workDate-error').text()).toContain('出诊日期不能早于今天')
    expect(http.post).not.toHaveBeenCalled()
    past.unmount()

    vi.setSystemTime(new Date('2026-11-25T08:00:00'))
    const started = await mountDrawer()
    await fillDrawer(started, { doctorId: 5, workDate: '2026-11-25', timeSlot: 1, totalNum: '1', price: '0' })
    await started.get('form').trigger('submit')
    await flushPromises()
    expect(started.get('#schedule-timeSlot-error').text()).toContain('该时段已开始，不能新增排班')
    expect(http.post).not.toHaveBeenCalled()
  })

  it('allows unchanged expired edit maintenance but not changing its date or slot', async () => {
    const expired: Schedule = { ...morning, workDate: '2026-11-24', timeSlot: 1, status: 0 }
    const unchanged = await mountDrawer(expired)
    await fillDrawer(unchanged, { totalNum: '22', price: '35' })
    await unchanged.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/schedule', { id: 51, timeSlot: 1, totalNum: 22, price: 35, status: 0 })
    expect(vi.mocked(http.put).mock.calls[0][1]).not.toHaveProperty('workDate')
    expect(unchanged.emitted('update:modelValue')).toEqual([[false]])
    expect(unchanged.emitted('saved')).toEqual([[morning]])
    unchanged.unmount()

    const changedDate = await mountDrawer(expired)
    await fillDrawer(changedDate, { workDate: '2026-11-23' })
    await changedDate.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledTimes(1)
    expect(changedDate.get('#schedule-workDate-error').text()).toContain('出诊日期不能早于今天')
    expect(http.put).toHaveBeenCalledTimes(1)
    changedDate.unmount()

    vi.setSystemTime(new Date('2026-11-25T18:00:00'))
    const changedSlot = await mountDrawer(expired)
    await fillDrawer(changedSlot, { timeSlot: 3 })
    await changedSlot.get('form').trigger('submit')
    await flushPromises()
    expect(changedSlot.get('#schedule-timeSlot-error').text()).toContain('不能将排班调整到已开始的时段')
    expect(http.put).toHaveBeenCalledTimes(1)
  })

  it('forbids switching an unchanged expired schedule back to available', async () => {
    const expired: Schedule = { ...morning, workDate: '2026-11-24', status: 0 }
    const wrapper = await mountDrawer(expired)
    await fillDrawer(wrapper, { status: 1 })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#schedule-status-error').text()).toContain('已过期排班不能改为可预约')
    expect(http.put).not.toHaveBeenCalled()
  })

  it('prevents reducing total below the already booked count', async () => {
    const wrapper = await mountDrawer(morning)
    await fillDrawer(wrapper, { totalNum: '4' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#schedule-totalNum-error').text()).toContain('已挂号 5 个')
    expect(http.put).not.toHaveBeenCalled()
  })

  it('shows option loading/error with retry and keeps save disabled', async () => {
    let finishOptions!: (value: Doctor[]) => void
    const ScheduleFormDrawer = await drawerComponent()
    vi.mocked(http.get).mockReturnValueOnce(new Promise((resolve) => { finishOptions = resolve }))
    const wrapper = mount(ScheduleFormDrawer, { props: { modelValue: true }, global: { stubs: { teleport: true, ...selectStubs } } })
    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载医生')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    finishOptions(doctors)
    await flushPromises()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()

    vi.mocked(http.get).mockRejectedValueOnce(new Error('医生选项加载失败')).mockResolvedValueOnce(doctors)
    const failed = await mountDrawer()
    expect(failed.get('.options-error').text()).toContain('医生选项加载失败')
    await buttonWithText(failed, '重试')!.trigger('click')
    await flushPromises()
    expect(failed.find('.options-error').exists()).toBe(false)
    expect(failed.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('locks duplicate saves and drawer close while pending, then reopens fresh after parent close', async () => {
    const ScheduleFormDrawer = await drawerComponent()
    let finishSave!: (value: Schedule) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = mount(defineComponent({
      components: { ScheduleFormDrawer },
      setup() {
        const open = ref(true)
        return { open, close: () => { open.value = false }, reopen: () => { open.value = true } }
      },
      template: `<ScheduleFormDrawer v-model="open" /><button class="parent-close" @click="close">父级关闭</button><button class="parent-reopen" @click="reopen">重新打开</button>`
    }), { attachTo: document.body, global: { stubs: { teleport: true, ...selectStubs } } })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(ScheduleFormDrawer)
    await fillDrawer(drawer, { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: '20', price: '30' })
    await drawer.get('form').trigger('submit')
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(1)
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    const close = document.querySelector<HTMLButtonElement>('.el-drawer__close-btn')!
    close.click()
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(true)
    expect(drawer.emitted('update:modelValue')).toBeUndefined()
    await wrapper.get('.parent-close').trigger('click')
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    await drawer.get('form').trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)

    finishSave(morning)
    await flushPromises()
    expect(drawer.emitted('saved')).toEqual([[morning]])
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="出诊日期"]').element as HTMLInputElement).value).toBe('')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await fillDrawer(drawer, { doctorId: 6, workDate: '2026-11-28', timeSlot: 2, totalNum: '2', price: '0' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('shows direct backend rejection text and allows correction', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('该医生在该时段已有排班'))
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { doctorId: 5, workDate: '2026-11-27', timeSlot: 1, totalNum: '20', price: '30' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.schedule-error').text()).toContain('该医生在该时段已有排班')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await fillDrawer(wrapper, { timeSlot: 2 })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('saved')).toEqual([[morning]])
  })
})

describe('ScheduleManageView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    setupHttp()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('combines paginated schedules with both lists and renders display names and values', async () => {
    const wrapper = await mountSchedules()
    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(http.get).toHaveBeenCalledWith('/admin/doctor/list')
    expect(http.get).toHaveBeenCalledWith('/admin/department/list')
    const rows = scheduleRows(wrapper)
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('张医生')
    expect(rows[0].text()).toContain('儿科')
    expect(rows[0].text()).toContain('2026-11-27')
    expect(rows[0].text()).toContain('上午')
    expect(rows[0].text()).toContain('20')
    expect(rows[0].text()).toContain('15')
    expect(rows[0].text()).toContain('30.50')
    expect(rows[0].text()).toContain('可预约')
    expect(rows[1].text()).toContain('李医生')
    expect(rows[1].text()).toContain('内科')
    expect(rows[1].text()).toContain('下午')
    expect(rows[1].text()).toContain('停诊')
    expect(wrapper.text()).not.toContain('doctorId')
    expect(wrapper.text()).not.toContain('departmentId')
  })

  it('shows explicit fallbacks for schedule references missing from either list', async () => {
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/schedule/page') return pageResult([morning])
      if (url === '/admin/doctor/list') return []
      if (url === '/admin/department/list') return []
      throw new Error(`Unexpected GET ${url}`)
    })
    const wrapper = await mountSchedules()
    expect(scheduleRows(wrapper)[0].text()).toContain('未知医生')
    expect(scheduleRows(wrapper)[0].text()).toContain('未知科室')
  })

  it('keeps rows hidden until schedules and both name mappings settle', async () => {
    let finishDoctors!: (value: Doctor[]) => void
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/schedule/page') return pageResult([morning])
      if (url === '/admin/doctor/list') return new Promise<Doctor[]>((resolve) => { finishDoctors = resolve })
      return departments
    })
    const wrapper = mount(await manageComponent(), { global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } } })
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载排班')
    expect(scheduleRows(wrapper)).toHaveLength(0)
    finishDoctors(doctors)
    await flushPromises()
    expect(scheduleRows(wrapper)[0].text()).toContain('张医生')
  })

  it('paginates server results ten at a time and ignores navigation while a page is pending', async () => {
    let finishSecond!: (value: PageResult<Schedule>) => void
    vi.mocked(http.get).mockImplementation(async (url, config) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      const num = Number(config?.params?.pageNum ?? 1)
      if (num === 1) return pageResult([morning], 21)
      if (num === 2) return new Promise<PageResult<Schedule>>((resolve) => { finishSecond = resolve })
      return pageResult([{ ...afternoon, id: 53 }], 21)
    })
    const wrapper = await mountSchedules()
    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeDefined()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', { params: { pageNum: 2, pageSize: 10 } })
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page')).toHaveLength(2)
    finishSecond(pageResult([afternoon], 21))
    await flushPromises()
    expect(scheduleRows(wrapper)[0].text()).toContain('李医生')
    expect(wrapper.text()).toContain('第 2 / 3 页，共 21 条')
  })

  it('filters by numeric doctor id on the server, resets to page one, and preserves the filter across pages', async () => {
    const wrapper = await mountSchedules()
    await wrapper.get('select[aria-label="按医生筛选"]').setValue('6')
    await buttonWithText(wrapper, '查询')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', { params: { pageNum: 1, pageSize: 10, doctorId: 6 } })
    await buttonWithText(wrapper, '重置')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } })

    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      return pageResult([afternoon], 11)
    })
    await wrapper.get('select[aria-label="按医生筛选"]').setValue('6')
    await buttonWithText(wrapper, '查询')!.trigger('click')
    await flushPromises()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 2, pageSize: 10, doctorId: 6 } }])
  })

  it('creates through the real drawer and refreshes the selected page and filter', async () => {
    const wrapper = await mountSchedules()
    await wrapper.get('select[aria-label="按医生筛选"]').setValue('5')
    await buttonWithText(wrapper, '查询')!.trigger('click')
    await flushPromises()
    const drawer = await startCreate(wrapper)
    await fillDrawer(drawer, { doctorId: 5, workDate: '2026-11-28', timeSlot: 1, totalNum: '8', price: '20.00', status: 1 })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/schedule', { doctorId: 5, workDate: '2026-11-28', timeSlot: 1, totalNum: 8, price: 20, status: 1 })
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 1, pageSize: 10, doctorId: 5 } }])
    expect(wrapper.findComponent({ name: 'ScheduleFormDrawer' }).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('新增成功')
  })

  it('edits through the real drawer without doctorId and refreshes the current page', async () => {
    const wrapper = await mountSchedules()
    const drawer = await startEdit(wrapper)
    await fillDrawer(drawer, { timeSlot: 3, totalNum: '25', price: '32.50', status: 0 })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/schedule', { id: 51, timeSlot: 3, totalNum: 25, price: 32.5, status: 0 })
    expect(vi.mocked(http.put).mock.calls[0][1]).not.toHaveProperty('workDate')
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } }])
    expect(wrapper.get('.action-success').text()).toContain('编辑成功')
  })

  it('requires named confirmation and supports cancellation', async () => {
    const wrapper = await mountSchedules()
    await scheduleRows(wrapper)[0].get('button[aria-label="删除排班 51"]').trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    expect(dialog.props('modelValue')).toBe(true)
    expect(dialog.props('message')).toContain('张医生')
    expect(dialog.props('message')).toContain('2026-11-27 上午')
    expect(http.delete).not.toHaveBeenCalled()
    dialog.vm.$emit('update:modelValue', false)
    await nextTick()
    expect(http.delete).not.toHaveBeenCalled()
  })

  it('deletes the exact id, closes confirmation, announces success, and refreshes', async () => {
    const wrapper = await mountSchedules()
    await confirmDelete(wrapper)
    expect(http.delete).toHaveBeenCalledWith('/admin/schedule/51')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
    expect(wrapper.get('.action-success').attributes('role')).toBe('status')
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
    expect(http.get).toHaveBeenCalledTimes(6)
  })

  it('guards duplicate deletes and cancellation through the request', async () => {
    let finishDelete!: () => void
    vi.mocked(http.delete).mockReturnValueOnce(new Promise<void>((resolve) => { finishDelete = resolve }))
    const wrapper = await mountSchedules()
    await scheduleRows(wrapper)[0].get('button[aria-label="删除排班 51"]').trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(http.delete).toHaveBeenCalledTimes(1)
    expect(dialog.props('modelValue')).toBe(false)
    expect(scheduleRows(wrapper)[0].get('button[aria-label="删除排班 51"]').attributes('disabled')).toBeDefined()
    expect(buttonWithText(wrapper, '新增排班')!.attributes('disabled')).toBeDefined()
    finishDelete()
    await flushPromises()
    expect(scheduleRows(wrapper)[0].get('button[aria-label="删除排班 51"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps all actions locked until the post-delete refresh settles', async () => {
    let finishDelete!: () => void
    let finishRefresh!: (value: PageResult<Schedule>) => void
    let pageLoads = 0
    vi.mocked(http.delete).mockReturnValueOnce(new Promise<void>((resolve) => { finishDelete = resolve }))
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([morning])
      return new Promise<PageResult<Schedule>>((resolve) => { finishRefresh = resolve })
    })
    const wrapper = await mountSchedules()
    await scheduleRows(wrapper)[0].get('button[aria-label="删除排班 51"]').trigger('click')
    wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
    await flushPromises()
    finishDelete()
    await flushPromises()
    expect(buttonWithText(wrapper, '新增排班')!.attributes('disabled')).toBeDefined()
    expect(buttonWithText(wrapper, '查询')!.attributes('disabled')).toBeDefined()
    finishRefresh(pageResult([]))
    await flushPromises()
    expect(buttonWithText(wrapper, '新增排班')!.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
  })

  it('shows the direct backend delete rejection and does not refresh or show success', async () => {
    vi.mocked(http.delete).mockRejectedValueOnce(new Error('排班存在挂号单，无法删除'))
    const wrapper = await mountSchedules()
    await confirmDelete(wrapper)
    expect(wrapper.get('.action-error').text()).toContain('排班存在挂号单，无法删除')
    expect(wrapper.find('.action-success').exists()).toBe(false)
    expect(http.get).toHaveBeenCalledTimes(3)
    expect(scheduleRows(wrapper)).toHaveLength(2)
  })

  it('recovers the last page using only the total in the new delete response', async () => {
    const loads: Record<number, number> = { 1: 0, 2: 0 }
    vi.mocked(http.get).mockImplementation(async (url, config) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      const num = Number(config?.params?.pageNum ?? 1)
      loads[num] += 1
      if (num === 1) return pageResult([morning], loads[1] === 1 ? 11 : 10)
      return loads[2] === 1 ? pageResult([afternoon], 11) : pageResult([], 10)
    })
    const wrapper = await mountSchedules()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, afternoon)
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } }])
    expect(wrapper.text()).toContain('第 1 / 1 页，共 10 条')
  })

  it('uses a concurrently reduced new total even when the deleted page response is empty', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([morning], 11)
      if (pageLoads === 2) return pageResult([afternoon], 11)
      if (pageLoads === 3) return pageResult([], 9)
      return pageResult([morning], 9)
    })
    const wrapper = await mountSchedules()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, afternoon)
    expect(wrapper.text()).toContain('第 1 / 1 页，共 9 条')
    expect(scheduleRows(wrapper)[0].text()).toContain('张医生')
  })

  it('does not move back when a concurrent insert keeps the current last page valid', async () => {
    let pageLoads = 0
    const inserted = { ...morning, id: 53, doctorId: 6, departmentId: 11 }
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([morning], 11)
      if (pageLoads === 2) return pageResult([afternoon], 11)
      return pageResult([inserted], 11)
    })
    const wrapper = await mountSchedules()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, afternoon)
    expect(pageLoads).toBe(3)
    expect(wrapper.text()).toContain('第 2 / 2 页，共 11 条')
    expect(scheduleRows(wrapper)[0].text()).toContain('李医生')
  })

  it.each(['create', 'update', 'delete'] as const)('preserves %s success separately from refresh failure and retries the same page', async (action) => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 2) throw new Error('排班列表暂时不可用')
      return pageResult([morning])
    })
    const wrapper = await mountSchedules()
    if (action === 'create') {
      const drawer = await startCreate(wrapper)
      await fillDrawer(drawer, { doctorId: 5, workDate: '2026-11-28', timeSlot: 1, totalNum: '2', price: '0' })
      await drawer.get('form').trigger('submit')
    } else if (action === 'update') {
      const drawer = await startEdit(wrapper)
      await fillDrawer(drawer, { status: 0 })
      await drawer.get('form').trigger('submit')
    } else await confirmDelete(wrapper)
    await flushPromises()
    const success = action === 'create' ? '新增成功' : action === 'update' ? '编辑成功' : '删除成功'
    expect(wrapper.get('.action-success').text()).toContain(success)
    expect(wrapper.get('.reload-warning').text()).toContain('排班列表暂时不可用')
    expect(scheduleRows(wrapper)).toHaveLength(0)
    await buttonWithText(wrapper, '重新加载')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.reload-warning').exists()).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain(success)
    expect(scheduleRows(wrapper)).toHaveLength(1)
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 1, pageSize: 10 } }])
  })

  it('hides stale rows on target-page failure and retries the still-selected page', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([morning], 21)
      if (pageLoads === 2) throw new Error('第二页加载失败')
      return pageResult([afternoon], 21)
    })
    const wrapper = await mountSchedules()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(ErrorState).props('message')).toBe('第二页加载失败')
    expect(scheduleRows(wrapper)).toHaveLength(0)
    expect(wrapper.text()).not.toContain('#51')
    expect(wrapper.text()).toContain('第 2 / 3 页，共 21 条')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/schedule/page').at(-1)).toEqual(['/admin/schedule/page', { params: { pageNum: 2, pageSize: 10 } }])
    expect(scheduleRows(wrapper)[0].text()).toContain('李医生')
  })

  it('surfaces mapping-load failures with retry instead of mislabeled rows', async () => {
    let doctorLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/schedule/page') return pageResult([morning])
      if (url === '/admin/department/list') return departments
      doctorLoads += 1
      if (doctorLoads === 1) throw new Error('医生映射失败')
      return doctors
    })
    const wrapper = await mountSchedules()
    expect(wrapper.findComponent(ErrorState).props('message')).toBe('医生映射失败')
    expect(scheduleRows(wrapper)).toHaveLength(0)
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(scheduleRows(wrapper)[0].text()).toContain('张医生')
  })

  it('shows loading followed by an empty state', async () => {
    let finishPage!: (value: PageResult<Schedule>) => void
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/doctor/list') return doctors
      if (url === '/admin/department/list') return departments
      return new Promise<PageResult<Schedule>>((resolve) => { finishPage = resolve })
    })
    const wrapper = mount(await manageComponent(), { global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } } })
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载排班')
    finishPage(pageResult([]))
    await flushPromises()
    expect(wrapper.text()).toContain('暂无排班')
  })
})

describe('admin schedule route and navigation', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    sessionStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('registers an admin-only lazy schedule route and preserves both existing admin routes', async () => {
    const matched = router.resolve('/admin/schedules')!
    expect(matched.meta.roles).toEqual([1])
    expect(matched.meta.requiresAuth).toBe(true)
    const loader = matched.matched.at(-1)!.components!.default!
    expect(typeof loader).toBe('function')
    expect((await (loader as () => Promise<{ default: unknown }>)()).default).toBe(await manageComponent())

    for (const [path, name] of [['/admin/departments', 'admin-departments'], ['/admin/doctors', 'admin-doctors']] as const) {
      const existing = router.resolve(path)!
      expect(existing.name).toBe(name)
      expect(existing.meta.roles).toEqual([1])
      expect(typeof existing.matched.at(-1)!.components!.default).toBe('function')
    }
  })

  it('renders a real schedule link with route-aware active state and title without breaking other links', async () => {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { id: 1, username: '管理员', role: 1, roleName: '管理员' }
    const wrapper = mount(AdminLayout, { global: { plugins: [router, pinia], stubs: { RouterView: true } } })
    await router.push('/admin/schedules')
    await flushPromises()
    const links = wrapper.findAllComponents({ name: 'RouterLink' })
    const scheduleLink = links.find((link) => link.props('to') === '/admin/schedules')!
    const doctorLink = links.find((link) => link.props('to') === '/admin/doctors')!
    const departmentLink = links.find((link) => link.props('to') === '/admin/departments')!
    expect(scheduleLink.text()).toContain('排班管理')
    expect(scheduleLink.classes()).toContain('is-active')
    expect(wrapper.get('h1').text()).toBe('排班管理')
    expect(wrapper.find('.admin-nav-item.is-disabled').exists()).toBe(false)

    await router.push('/admin/doctors')
    await flushPromises()
    expect(scheduleLink.classes()).not.toContain('is-active')
    expect(doctorLink.classes()).toContain('is-active')
    expect(wrapper.get('h1').text()).toBe('医生管理')

    await router.push('/admin/departments')
    await flushPromises()
    expect(doctorLink.classes()).not.toContain('is-active')
    expect(departmentLink.classes()).toContain('is-active')
    expect(wrapper.get('h1').text()).toBe('科室管理')
    wrapper.unmount()
  })

  it('retains the department heading fallback outside admin pages', async () => {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { id: 1, username: '管理员', role: 1, roleName: '管理员' }
    const wrapper = mount(AdminLayout, { global: { plugins: [router, pinia], stubs: { RouterView: true } } })
    await router.push('/forbidden')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('科室管理')
    wrapper.unmount()
  })
})
