import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick, ref } from 'vue'
import { ElLoading, ElMessage } from 'element-plus'
import { adminDoctorApi } from '@/api/adminDoctor'
import { http } from '@/api/http'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DoctorFormDrawer from '@/components/DoctorFormDrawer.vue'
import ErrorState from '@/components/ErrorState.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import SearchBar from '@/components/SearchBar.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import DoctorManageView from '@/views/admin/DoctorManageView.vue'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { Department, Doctor, PageResult } from '@/types/models'

vi.mock('@/api/http', () => ({
  TOKEN_KEY: 'token',
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

const pediatrics: Department = { id: 3, name: '儿科', description: '儿童诊疗' }
const internal: Department = { id: 11, name: '内科', description: '成人内科诊疗' }
const dental: Department = { id: 9, name: '口腔科' }
const zhang: Doctor = { id: 5, departmentId: 3, name: '张医生', title: '主任医师', intro: '儿童呼吸诊疗', createTime: '2026-11-25T08:00:00', updateTime: '2026-11-25T09:00:00' }
const li: Doctor = { id: 6, departmentId: 11, name: '李医生', intro: '成人心血管诊疗' }
const wang: Doctor = { id: 7, departmentId: 9, name: '王医生' }
const departments = [pediatrics, internal, dental]

function pageResult(records: Doctor[], total = records.length): PageResult<Doctor> {
  return { total, records }
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function doctorRows(wrapper: ReturnType<typeof mount>) {
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

async function mountDoctors() {
  const wrapper = mount(DoctorManageView, {
    attachTo: document.body,
    global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } }
  })
  await flushPromises()
  return wrapper
}

async function mountDrawer(doctor: Doctor | null = null) {
  const wrapper = mount(DoctorFormDrawer, {
    props: { modelValue: true, doctor },
    attachTo: document.body,
    global: { stubs: { teleport: true, ...selectStubs } }
  })
  await nextTick()
  await flushPromises()
  return wrapper
}

async function fillDrawer(drawer: ReturnType<typeof mount>, values: { departmentId?: number; name?: string; title?: string; intro?: string } = {}) {
  if (values.departmentId !== undefined) await drawer.get('select[aria-label="请选择科室"]').setValue(String(values.departmentId))
  if (values.name !== undefined) await drawer.get('input[aria-label="医生姓名"]').setValue(values.name)
  if (values.title !== undefined) await drawer.get('input[aria-label="职称"]').setValue(values.title)
  if (values.intro !== undefined) await drawer.get('textarea[aria-label="医生简介"]').setValue(values.intro)
}

async function startCreate(wrapper: ReturnType<typeof mount>) {
  await buttonWithText(wrapper, '新增医生')!.trigger('click')
  await nextTick()
  return wrapper.findComponent(DoctorFormDrawer)
}

async function startEdit(wrapper: ReturnType<typeof mount>, doctor = zhang) {
  const row = doctorRows(wrapper).find((item) => item.text().includes(doctor.name))!
  await row.get(`button[aria-label="编辑医生 ${doctor.name}"]`).trigger('click')
  await nextTick()
  return wrapper.findComponent(DoctorFormDrawer)
}

async function confirmDelete(wrapper: ReturnType<typeof mount>, doctor = zhang) {
  const row = doctorRows(wrapper).find((item) => item.text().includes(doctor.name))!
  await row.get(`button[aria-label="删除医生 ${doctor.name}"]`).trigger('click')
  wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
  await flushPromises()
}

function setupHttp(page: PageResult<Doctor> = pageResult([zhang, li, wang])) {
  vi.mocked(http.get).mockImplementation(async (url) => {
    if (url === '/admin/department/list') return departments
    if (String(url).startsWith('/admin/doctor/page')) return page
    throw new Error(`Unexpected GET ${url}`)
  })
  vi.mocked(http.post).mockResolvedValue(zhang)
  vi.mocked(http.put).mockResolvedValue(zhang)
  vi.mocked(http.delete).mockResolvedValue(undefined)
}

describe('admin doctor api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue(undefined)
    vi.mocked(http.post).mockResolvedValue(zhang)
    vi.mocked(http.put).mockResolvedValue(zhang)
    vi.mocked(http.delete).mockResolvedValue(undefined)
  })

  it('creates a doctor without account binding', async () => {
    const payload = { departmentId: 11, name: '张医生', title: '主任医师', intro: '呼吸科' }
    await adminDoctorApi.create(payload)
    expect(http.post).toHaveBeenCalledWith('/admin/doctor', payload)
    expect(vi.mocked(http.post).mock.calls[0][1]).not.toHaveProperty('userId')
  })

  it('updates all trimmed edit fields and a numeric id without userId', async () => {
    const payload = { id: 5, departmentId: 11, name: '张医生', title: '', intro: '呼吸科' }
    await adminDoctorApi.update(payload)
    expect(http.put).toHaveBeenCalledWith('/admin/doctor', payload)
    expect(vi.mocked(http.put).mock.calls[0][1]).not.toHaveProperty('userId')
  })

  it('loads the first ten records by default and accepts custom pagination', async () => {
    await adminDoctorApi.page()
    await adminDoctorApi.page(4, 25)
    expect(http.get).toHaveBeenNthCalledWith(1, '/admin/doctor/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/admin/doctor/page', { params: { pageNum: 4, pageSize: 25 } })
  })

  it('loads the doctor list separately', async () => {
    await adminDoctorApi.list()
    expect(http.get).toHaveBeenCalledWith('/admin/doctor/list')
  })

  it('deletes the exact numeric doctor id', async () => {
    await adminDoctorApi.remove(5)
    expect(http.delete).toHaveBeenCalledWith('/admin/doctor/5')
  })
})

describe('DoctorFormDrawer', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue(departments)
    vi.mocked(http.post).mockResolvedValue(zhang)
    vi.mocked(http.put).mockResolvedValue(zhang)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('loads readable department label/id options and creates with trimmed values', async () => {
    const wrapper = await mountDrawer()
    const select = wrapper.findComponent(NameIdSelect)
    expect(select.props('options')).toEqual([{ label: '儿科', value: 3 }, { label: '内科', value: 11 }, { label: '口腔科', value: 9 }])
    expect(http.get).toHaveBeenCalledWith('/admin/department/list')
    expect(wrapper.find('input[name="userId"], select[name="userId"]').exists()).toBe(false)

    await fillDrawer(wrapper, { departmentId: 11, name: ' 张医生 ', title: ' 主任医师 ', intro: ' 呼吸诊疗 ' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/admin/doctor', { departmentId: 11, name: '张医生', title: '主任医师', intro: '呼吸诊疗' })
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('saved')).toEqual([[zhang]])
  })

  it('prefills all edit values and updates with full trimmed values and numeric id', async () => {
    const wrapper = await mountDrawer(zhang)
    expect((wrapper.get('input[aria-label="医生姓名"]').element as HTMLInputElement).value).toBe('张医生')
    expect((wrapper.get('input[aria-label="职称"]').element as HTMLInputElement).value).toBe('主任医师')
    expect((wrapper.get('textarea[aria-label="医生简介"]').element as HTMLTextAreaElement).value).toBe('儿童呼吸诊疗')
    expect((wrapper.get('select[aria-label="请选择科室"]').element as HTMLSelectElement).value).toBe('3')

    await fillDrawer(wrapper, { departmentId: 9, name: ' 王医生 ', title: ' 副主任医师 ', intro: ' 口腔保健 ' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/doctor', { id: 5, departmentId: 9, name: '王医生', title: '副主任医师', intro: '口腔保健' })
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toEqual([[zhang]])
  })

  it.each([
    ['departmentId', '', '请选择科室'],
    ['name', '   ', '请输入医生姓名'],
    ['name', '医'.repeat(33), '医生姓名不能超过 32 个字'],
    ['title', '职'.repeat(33), '职称不能超过 32 个字'],
    ['intro', '简'.repeat(256), '医生简介不能超过 255 个字']
  ])('rejects invalid %s with an accessible field error', async (field, value, message) => {
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { departmentId: 3, name: '张医生', [field]: value })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const selector = field === 'departmentId' ? 'select[aria-label="请选择科室"]' : field === 'name' ? 'input[aria-label="医生姓名"]' : field === 'title' ? 'input[aria-label="职称"]' : 'textarea[aria-label="医生简介"]'
    const input = wrapper.get(selector)
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${input.attributes('aria-describedby')}`).text()).toContain(message)
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('accepts exact length limits and sends empty optional text', async () => {
    const wrapper = await mountDrawer()
    const name = '医'.repeat(32)
    const title = '职'.repeat(32)
    const intro = '简'.repeat(255)
    await fillDrawer(wrapper, { departmentId: 3, name, title, intro })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/doctor', { departmentId: 3, name, title, intro })
  })

  it('exposes required semantics for department and name', async () => {
    const wrapper = await mountDrawer()
    expect(wrapper.get('select[aria-label="请选择科室"]').attributes('aria-required')).toBe('true')
    const name = wrapper.get('input[aria-label="医生姓名"]')
    expect(name.attributes('required')).toBeDefined()
    expect(name.attributes('aria-required')).toBe('true')
  })

  it('shows option loading and visible error with retry before enabling save', async () => {
    let finishOptions!: (value: Department[]) => void
    vi.mocked(http.get).mockReturnValueOnce(new Promise((resolve) => { finishOptions = resolve }))
    const wrapper = mount(DoctorFormDrawer, { props: { modelValue: true }, global: { stubs: { teleport: true, ...selectStubs } } })
    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载科室')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    finishOptions(departments)
    await flushPromises()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()

    vi.mocked(http.get).mockRejectedValueOnce(new Error('科室选项加载失败')).mockResolvedValueOnce(departments)
    const failed = await mountDrawer()
    expect(failed.get('.options-error').text()).toContain('科室选项加载失败')
    expect(failed.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await buttonWithText(failed, '重试')!.trigger('click')
    await flushPromises()
    expect(failed.find('.options-error').exists()).toBe(false)
    expect(failed.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    failed.unmount()
  })

  it('locks duplicate submits and real drawer close while saving', async () => {
    vi.useFakeTimers()
    let finishSave!: (value: Doctor) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { departmentId: 3, name: '张医生' })
    const form = wrapper.get('form')
    await form.trigger('submit')
    await form.trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input[aria-label="医生姓名"]').attributes('disabled')).toBeDefined()
    const close = document.querySelector<HTMLButtonElement>('.el-drawer__close-btn')!
    close.click()
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.props('modelValue')).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    finishSave(zhang)
    await flushPromises()
    expect(wrapper.emitted('saved')).toEqual([[zhang]])
    wrapper.unmount()
  })

  it('reopens fresh and usable when a pending save resolves after parent close', async () => {
    let finishSave!: (value: Doctor) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = mount(defineComponent({
      components: { DoctorFormDrawer },
      setup() {
        const open = ref(true)
        return { open, close: () => { open.value = false }, reopen: () => { open.value = true } }
      },
      template: `<DoctorFormDrawer v-model="open" /><button class="parent-close" @click="close">父级关闭</button><button class="parent-reopen" @click="reopen">重新打开</button>`
    }), { attachTo: document.body, global: { stubs: { teleport: true, ...selectStubs } } })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(DoctorFormDrawer)
    await fillDrawer(drawer, { departmentId: 3, name: '张医生' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('.parent-close').trigger('click')
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="医生姓名"]').element as HTMLInputElement).value).toBe('张医生')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await drawer.get('form').trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)

    finishSave(zhang)
    await flushPromises()
    expect(drawer.emitted('saved')).toEqual([[zhang]])
    expect(drawer.emitted('update:modelValue')).toEqual([[false]])
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="医生姓名"]').element as HTMLInputElement).value).toBe('')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await fillDrawer(drawer, { departmentId: 9, name: '王医生' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('reopens a fresh usable form when the save resolves while the parent has closed it', async () => {
    let finishSave!: (value: Doctor) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = mount(defineComponent({
      components: { DoctorFormDrawer },
      setup() {
        const open = ref(true)
        return { open, close: () => { open.value = false }, reopen: () => { open.value = true } }
      },
      template: `<DoctorFormDrawer v-model="open" /><button class="parent-close" @click="close">父级关闭</button><button class="parent-reopen" @click="reopen">重新打开</button>`
    }), { attachTo: document.body, global: { stubs: { teleport: true, ...selectStubs } } })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(DoctorFormDrawer)
    await fillDrawer(drawer, { departmentId: 3, name: '张医生' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('.parent-close').trigger('click')
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(false)

    finishSave(zhang)
    await flushPromises()
    expect(drawer.emitted('saved')).toEqual([[zhang]])
    expect(drawer.emitted('update:modelValue')).toEqual([[false]])
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="医生姓名"]').element as HTMLInputElement).value).toBe('')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await fillDrawer(drawer, { departmentId: 9, name: '王医生' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    expect(http.post).toHaveBeenLastCalledWith('/admin/doctor', { departmentId: 9, name: '王医生', title: '', intro: '' })
    wrapper.unmount()
  })

  it('shows a direct backend save error and allows correction', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('医生姓名已存在'))
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, { departmentId: 3, name: '张医生' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.doctor-error').text()).toContain('医生姓名已存在')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await fillDrawer(wrapper, { name: '李医生' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('saved')).toEqual([[zhang]])
  })
})

describe('DoctorManageView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    setupHttp()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('loads page and department mapping together and renders names, never raw department ids', async () => {
    const wrapper = await mountDoctors()
    expect(http.get).toHaveBeenCalledWith('/admin/doctor/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(http.get).toHaveBeenCalledWith('/admin/department/list')
    const rows = doctorRows(wrapper)
    expect(rows).toHaveLength(3)
    expect(rows[0].text()).toContain('张医生')
    expect(rows[0].text()).toContain('儿科')
    expect(rows[0].text()).toContain('主任医师')
    expect(rows[0].text()).toContain('儿童呼吸诊疗')
    expect(rows[1].text()).toContain('内科')
    expect(rows[1].text()).toContain('成人心血管诊疗')
    expect(rows[2].text()).toContain('—')
    expect(wrapper.text()).not.toContain('departmentId')
  })

  it('keeps rows hidden until both page and mapping settle', async () => {
    let finishDepartments!: (value: Department[]) => void
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return new Promise<Department[]>((resolve) => { finishDepartments = resolve })
      return pageResult([zhang])
    })
    const wrapper = mount(DoctorManageView, { global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } } })
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载医生')
    expect(doctorRows(wrapper)).toHaveLength(0)
    finishDepartments(departments)
    await flushPromises()
    expect(doctorRows(wrapper)[0].text()).toContain('儿科')
  })

  it('paginates server results with ten records per page', async () => {
    let requestedPage = 1
    vi.mocked(http.get).mockImplementation(async (url, config) => {
      if (url === '/admin/department/list') return departments
      requestedPage = Number(config?.params?.pageNum ?? 1)
      return requestedPage === 1 ? pageResult([zhang], 11) : pageResult([li], 11)
    })
    const wrapper = await mountDoctors()
    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeDefined()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/admin/doctor/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(doctorRows(wrapper)).toHaveLength(1)
    expect(doctorRows(wrapper)[0].text()).toContain('李医生')
    expect(buttonWithText(wrapper, '下一页')!.attributes('disabled')).toBeDefined()
  })

  it('filters current-page names case-insensitively, clears, and preserves the filter across pages', async () => {
    const first = { ...zhang, name: 'Alice' }
    const second = { ...li, name: 'ALICE Chen' }
    vi.mocked(http.get).mockImplementation(async (url, config) => url === '/admin/department/list'
      ? departments
      : Number(config?.params?.pageNum) === 1 ? pageResult([first, wang], 11) : pageResult([second], 11))
    const wrapper = await mountDoctors()
    await wrapper.get('input').setValue(' 内 ')
    expect(doctorRows(wrapper)).toHaveLength(2)
    await wrapper.get('input').trigger('keyup.enter')
    expect(doctorRows(wrapper)).toHaveLength(0)
    await wrapper.get('input').setValue(' ALICE ')
    await wrapper.get('input').trigger('keyup.enter')
    expect(doctorRows(wrapper)).toHaveLength(1)
    expect(doctorRows(wrapper)[0].text()).toContain('Alice')
    await wrapper.get('input').setValue('')
    await flushPromises()
    expect(doctorRows(wrapper)).toHaveLength(2)
    await wrapper.get('input').setValue(' ALICE ')
    await wrapper.get('input').trigger('keyup.enter')
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(doctorRows(wrapper)).toHaveLength(1)
    expect(doctorRows(wrapper)[0].text()).toContain('ALICE Chen')
    await wrapper.get('input').setValue('')
    await flushPromises()
    expect(wrapper.findComponent(SearchBar).props('modelValue')).toBe('')
  })

  it('creates and refreshes the current page', async () => {
    let loads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      loads += 1
      return loads === 2 ? pageResult([li], 11) : pageResult([zhang], 11)
    })
    const wrapper = await mountDoctors()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    const drawer = await startCreate(wrapper)
    await fillDrawer(drawer, { departmentId: 9, name: ' 王医生 ', title: ' 主治医师 ', intro: ' 口腔 ' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/doctor', { departmentId: 9, name: '王医生', title: '主治医师', intro: '口腔' })
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/doctor/page').at(-1)).toEqual(['/admin/doctor/page', { params: { pageNum: 2, pageSize: 10 } }])
    expect(wrapper.findComponent(DoctorFormDrawer).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('新增成功')
  })

  it('edits and refreshes the current page', async () => {
    const wrapper = await mountDoctors()
    const drawer = await startEdit(wrapper)
    await fillDrawer(drawer, { departmentId: 11, name: ' 张主任 ', title: ' 主任医师 ', intro: ' 更新简介 ' })
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/doctor', { id: 5, departmentId: 11, name: '张主任', title: '主任医师', intro: '更新简介' })
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/doctor/page').at(-1)).toEqual(['/admin/doctor/page', { params: { pageNum: 1, pageSize: 10 } }])
    expect(wrapper.get('.action-success').text()).toContain('编辑成功')
  })

  it('requires named confirmation and supports cancellation', async () => {
    const wrapper = await mountDoctors()
    await doctorRows(wrapper)[0].get('button[aria-label="删除医生 张医生"]').trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    expect(dialog.props('modelValue')).toBe(true)
    expect(dialog.props('message')).toContain('张医生')
    expect(http.delete).not.toHaveBeenCalled()
    dialog.vm.$emit('update:modelValue', false)
    await nextTick()
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(http.delete).not.toHaveBeenCalled()
  })

  it('deletes the exact id, closes confirmation, and refreshes', async () => {
    const wrapper = await mountDoctors()
    await confirmDelete(wrapper)
    expect(http.delete).toHaveBeenCalledWith('/admin/doctor/5')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
    expect(http.get).toHaveBeenCalledTimes(4)
  })

  it('guards duplicate deletes through request and refresh and prevents cancellation during request', async () => {
    let finishDelete!: () => void
    vi.mocked(http.delete).mockReturnValueOnce(new Promise<void>((resolve) => { finishDelete = resolve }))
    const wrapper = await mountDoctors()
    await doctorRows(wrapper)[0].get('button[aria-label="删除医生 张医生"]').trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(http.delete).toHaveBeenCalledTimes(1)
    expect(dialog.props('modelValue')).toBe(false)
    expect(doctorRows(wrapper)[0].get('button[aria-label="删除医生 张医生"]').attributes('disabled')).toBeDefined()
    finishDelete()
    await flushPromises()
    expect(doctorRows(wrapper)[0].get('button[aria-label="删除医生 张医生"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps delete actions locked until the post-delete refresh settles', async () => {
    let finishDelete!: () => void
    let finishRefresh!: (value: PageResult<Doctor>) => void
    let pageLoads = 0
    vi.mocked(http.delete).mockReturnValueOnce(new Promise<void>((resolve) => { finishDelete = resolve }))
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([zhang])
      return new Promise<PageResult<Doctor>>((resolve) => { finishRefresh = resolve })
    })
    const wrapper = await mountDoctors()
    await doctorRows(wrapper)[0].get('button[aria-label="删除医生 张医生"]').trigger('click')
    wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
    await flushPromises()
    finishDelete()
    await flushPromises()
    expect(buttonWithText(wrapper, '新增医生')!.attributes('disabled')).toBeDefined()
    expect(pageLoads).toBe(2)
    finishRefresh(pageResult([]))
    await flushPromises()
    expect(buttonWithText(wrapper, '新增医生')!.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
  })

  it('closes confirmation and prominently shows the exact backend delete rejection', async () => {
    vi.mocked(http.delete).mockRejectedValueOnce(new Error('医生存在排班，无法删除'))
    const wrapper = await mountDoctors()
    await confirmDelete(wrapper)
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-error').text()).toContain('医生存在排班，无法删除')
    expect(wrapper.find('.action-success').exists()).toBe(false)
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(doctorRows(wrapper)).toHaveLength(3)
  })

  it('moves back based on the NEW total after deleting the only row on the last page', async () => {
    const pageLoads: Record<number, number> = { 1: 0, 2: 0 }
    vi.mocked(http.get).mockImplementation(async (url, config) => {
      if (url === '/admin/department/list') return departments
      const num = Number(config?.params?.pageNum ?? 1)
      pageLoads[num] += 1
      if (num === 1) return pageResult([zhang], pageLoads[1] === 1 ? 11 : 10)
      return pageLoads[2] === 1 ? pageResult([li], 11) : pageResult([], 10)
    })
    const wrapper = await mountDoctors()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, li)
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/doctor/page').at(-1)).toEqual(['/admin/doctor/page', { params: { pageNum: 1, pageSize: 10 } }])
    expect(wrapper.text()).toContain('第 1 / 1 页，共 10 条')
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
  })

  it('recovers using a concurrently reduced total that still leaves the page out of range', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([zhang], 11)
      if (pageLoads === 2) return pageResult([li], 11)
      if (pageLoads === 3) return pageResult([], 9)
      return pageResult([zhang], 9)
    })
    const wrapper = await mountDoctors()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, li)
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/doctor/page').at(-1)).toEqual(['/admin/doctor/page', { params: { pageNum: 1, pageSize: 10 } }])
    expect(wrapper.text()).toContain('第 1 / 1 页，共 9 条')
  })

  it('does not fall back when a concurrent insert makes the current last page valid', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([zhang], 11)
      if (pageLoads === 2) return pageResult([li], 11)
      return pageResult([wang], 11)
    })
    const wrapper = await mountDoctors()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, li)
    expect(pageLoads).toBe(3)
    expect(wrapper.text()).toContain('第 2 / 2 页，共 11 条')
    expect(doctorRows(wrapper)[0].text()).toContain('王医生')
  })

  it.each(['create', 'update', 'delete'] as const)('preserves %s success when refresh fails and retries separately', async (action) => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 2) throw new Error('医生列表暂时不可用')
      return pageResult([zhang])
    })
    const wrapper = await mountDoctors()
    if (action === 'create') {
      const drawer = await startCreate(wrapper)
      await fillDrawer(drawer, { departmentId: 3, name: '张医生' })
      await drawer.get('form').trigger('submit')
    } else if (action === 'update') {
      const drawer = await startEdit(wrapper)
      await fillDrawer(drawer, { departmentId: 3, name: '张主任' })
      await drawer.get('form').trigger('submit')
    } else await confirmDelete(wrapper)
    await flushPromises()
    const success = action === 'create' ? '新增成功' : action === 'update' ? '编辑成功' : '删除成功'
    expect(wrapper.get('.action-success').text()).toContain(success)
    expect(wrapper.get('.reload-warning').text()).toContain('医生列表暂时不可用')
    expect(doctorRows(wrapper)).toHaveLength(0)
    await buttonWithText(wrapper, '重新加载')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.reload-warning').exists()).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain(success)
    expect(doctorRows(wrapper)).toHaveLength(1)
  })

  it('shows a direct initial load error with a working retry', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) throw new Error('医生加载失败')
      return pageResult([zhang])
    })
    const wrapper = await mountDoctors()
    expect(wrapper.findComponent(ErrorState).props('message')).toBe('医生加载失败')
    expect(doctorRows(wrapper)).toHaveLength(0)
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(doctorRows(wrapper)).toHaveLength(1)
  })

  it('hides stale rows on target page failure and retries the still-selected page', async () => {
    let pageLoads = 0
    vi.mocked(http.get).mockImplementation(async (url) => {
      if (url === '/admin/department/list') return departments
      pageLoads += 1
      if (pageLoads === 1) return pageResult([zhang], 21)
      if (pageLoads === 2) throw new Error('第二页加载失败')
      return pageResult([li], 21)
    })
    const wrapper = await mountDoctors()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(ErrorState).props('message')).toBe('第二页加载失败')
    expect(doctorRows(wrapper)).toHaveLength(0)
    expect(wrapper.text()).not.toContain('儿童呼吸诊疗')
    expect(wrapper.text()).toContain('第 2 / 3 页，共 21 条')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/admin/doctor/page').at(-1)).toEqual(['/admin/doctor/page', { params: { pageNum: 2, pageSize: 10 } }])
    expect(doctorRows(wrapper)[0].text()).toContain('李医生')
  })

  it('shows loading then an empty state', async () => {
    let finishPage!: (value: PageResult<Doctor>) => void
    vi.mocked(http.get).mockImplementation(async (url) => url === '/admin/department/list'
      ? departments
      : new Promise<PageResult<Doctor>>((resolve) => { finishPage = resolve }))
    const wrapper = mount(DoctorManageView, { global: { stubs: { teleport: true, ...selectStubs }, directives: { loading: ElLoading.directive } } })
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载医生')
    finishPage(pageResult([]))
    await flushPromises()
    expect(wrapper.text()).toContain('暂无医生')
  })

  it('announces mutation success accessibly and through toast', async () => {
    const wrapper = await mountDoctors()
    await confirmDelete(wrapper)
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
    expect(wrapper.get('.action-success').attributes('role')).toBe('status')
  })

  it('keeps the admin role guard and exposes the real lazy doctor component', async () => {
    const matched = router.resolve('/admin/doctors')!
    expect(matched.meta.roles).toEqual([1])
    expect(matched.meta.requiresAuth).toBe(true)
    const loader = matched.matched.at(-1)!.components!.default!
    expect(typeof loader).toBe('function')
    const component = await (loader as () => Promise<{ default: unknown }>)()
    expect(component.default).toBe(DoctorManageView)
  })
})

describe('AdminLayout doctor navigation', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    sessionStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  async function mountLayout(path: string) {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.user = { id: 1, username: '管理员', role: 1, roleName: '管理员' }
    const wrapper = mount(AdminLayout, { global: { plugins: [router, pinia], stubs: { RouterView: true } } })
    await router.push(path)
    await flushPromises()
    return wrapper
  }

  it('renders a real doctor RouterLink and route-aware active styling and title', async () => {
    const wrapper = await mountLayout('/admin/doctors')
    const links = wrapper.findAllComponents({ name: 'RouterLink' })
    const doctorLink = links.find((link) => link.props('to') === '/admin/doctors')!
    expect(doctorLink.text()).toContain('医生管理')
    expect(doctorLink.classes()).toContain('is-active')
    expect(wrapper.get('h1').text()).toBe('医生管理')
    const scheduleLink = links.find((link) => link.props('to') === '/admin/schedules')!
    expect(scheduleLink.text()).toContain('排班管理')
    expect(scheduleLink.classes()).not.toContain('is-active')
    await router.push('/admin/departments')
    await flushPromises()
    expect(doctorLink.classes()).not.toContain('is-active')
    expect(wrapper.get('h1').text()).toBe('科室管理')
    wrapper.unmount()
  })

  it('defaults the heading to department management outside the two admin routes', async () => {
    const wrapper = await mountLayout('/forbidden')
    expect(wrapper.get('h1').text()).toBe('科室管理')
    wrapper.unmount()
  })
})
