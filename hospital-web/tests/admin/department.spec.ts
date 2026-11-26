import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { ElLoading, ElMessage } from 'element-plus'
import { adminDepartmentApi } from '@/api/adminDepartment'
import { http } from '@/api/http'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DepartmentFormDrawer from '@/components/DepartmentFormDrawer.vue'
import ErrorState from '@/components/ErrorState.vue'
import SearchBar from '@/components/SearchBar.vue'
import DepartmentManageView from '@/views/admin/DepartmentManageView.vue'
import router from '@/router'
import type { Department, PageResult } from '@/types/models'

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn() } }
})

const pediatrics: Department = {
  id: 3,
  name: '儿科',
  description: '儿童诊疗',
  createTime: '2026-11-25T08:00:00',
  updateTime: '2026-11-25T09:00:00'
}
const internal: Department = { id: 7, name: '内科', description: '成人内科诊疗' }
const dental: Department = { id: 9, name: '口腔科' }

function pageResult(records: Department[], total = records.length): PageResult<Department> {
  return { total, records }
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function departmentRows(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.el-table__body-wrapper tbody tr.el-table__row')
}

async function mountDepartments() {
  const wrapper = mount(DepartmentManageView, {
    attachTo: document.body,
    global: {
      stubs: { teleport: true },
      directives: { loading: ElLoading.directive }
    }
  })
  await flushPromises()
  return wrapper
}

async function fillDrawer(drawer: ReturnType<typeof mount>, name: string, description = '') {
  await drawer.get('input[aria-label="科室名称"]').setValue(name)
  await drawer.get('textarea[aria-label="科室简介"]').setValue(description)
}

async function startCreate(wrapper: ReturnType<typeof mount>) {
  await buttonWithText(wrapper, '新增科室')!.trigger('click')
  await nextTick()
  return wrapper.findComponent(DepartmentFormDrawer)
}

async function startEdit(wrapper: ReturnType<typeof mount>, department = pediatrics) {
  const row = departmentRows(wrapper).find((item) => item.text().includes(department.name))!
  await row.get(`button[aria-label="编辑科室 ${department.name}"]`).trigger('click')
  await nextTick()
  return wrapper.findComponent(DepartmentFormDrawer)
}

async function confirmDelete(wrapper: ReturnType<typeof mount>, department = pediatrics) {
  const row = departmentRows(wrapper).find((item) => item.text().includes(department.name))!
  await row.get(`button[aria-label="删除科室 ${department.name}"]`).trigger('click')
  wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
  await flushPromises()
}

describe('admin department api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue(undefined)
    vi.mocked(http.post).mockResolvedValue(pediatrics)
    vi.mocked(http.put).mockResolvedValue(pediatrics)
    vi.mocked(http.delete).mockResolvedValue(undefined)
  })

  it('posts only name and description on create', async () => {
    await adminDepartmentApi.create({ name: '儿科', description: '儿童诊疗' })
    expect(http.post).toHaveBeenCalledWith('/admin/department', { name: '儿科', description: '儿童诊疗' })
  })

  it('puts the numeric id, name, and description on update', async () => {
    await adminDepartmentApi.update({ id: 3, name: '儿科', description: '儿童诊疗' })
    expect(http.put).toHaveBeenCalledWith('/admin/department', { id: 3, name: '儿科', description: '儿童诊疗' })
  })

  it('loads the first ten records by default', async () => {
    await adminDepartmentApi.page()
    expect(http.get).toHaveBeenCalledWith('/admin/department/page', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('passes custom page parameters and loads the department list separately', async () => {
    await adminDepartmentApi.page(4, 25)
    await adminDepartmentApi.list()
    expect(http.get).toHaveBeenNthCalledWith(1, '/admin/department/page', { params: { pageNum: 4, pageSize: 25 } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/admin/department/list')
  })

  it('deletes the exact numeric department id', async () => {
    await adminDepartmentApi.remove(9)
    expect(http.delete).toHaveBeenCalledWith('/admin/department/9')
  })
})

describe('DepartmentFormDrawer', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.post).mockResolvedValue(pediatrics)
    vi.mocked(http.put).mockResolvedValue(pediatrics)
  })

  async function mountDrawer(department: Department | null = null) {
    const wrapper = mount(DepartmentFormDrawer, {
      props: { modelValue: true, department },
      attachTo: document.body,
      global: { stubs: { teleport: true } }
    })
    await nextTick()
    await flushPromises()
    return wrapper
  }

  it('creates with trimmed name and description, closes, and emits saved', async () => {
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, '  儿科  ', '  儿童诊疗  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/admin/department', { name: '儿科', description: '儿童诊疗' })
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('saved')).toEqual([[pediatrics]])
  })

  it('prefills an edit and updates with a numeric id', async () => {
    const wrapper = await mountDrawer(pediatrics)
    expect((wrapper.get('input[aria-label="科室名称"]').element as HTMLInputElement).value).toBe('儿科')
    expect((wrapper.get('textarea[aria-label="科室简介"]').element as HTMLTextAreaElement).value).toBe('儿童诊疗')

    await fillDrawer(wrapper, ' 小儿科 ', ' 儿童与青少年诊疗 ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/admin/department', {
      id: 3,
      name: '小儿科',
      description: '儿童与青少年诊疗'
    })
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toEqual([[pediatrics]])
  })

  it.each([
    ['科室名称', '   ', '请输入科室名称'],
    ['科室名称', '科'.repeat(33), '科室名称不能超过 32 个字'],
    ['科室简介', '简'.repeat(256), '科室简介不能超过 255 个字']
  ])('rejects invalid %s with an accessible field error', async (label, value, message) => {
    const wrapper = await mountDrawer()
    const selector = label === '科室名称' ? 'input[aria-label="科室名称"]' : 'textarea[aria-label="科室简介"]'
    if (label === '科室简介') await fillDrawer(wrapper, '儿科', value)
    else await wrapper.get(selector).setValue(value)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const field = wrapper.get(selector)
    expect(field.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${field.attributes('aria-describedby')}`).text()).toContain(message)
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('accepts the exact name and description length limits', async () => {
    const wrapper = await mountDrawer()
    const name = '科'.repeat(32)
    const description = '简'.repeat(255)
    await fillDrawer(wrapper, name, description)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/admin/department', { name, description })
  })

  it('exposes required semantics for the department name', async () => {
    const wrapper = await mountDrawer()
    const name = wrapper.get('input[aria-label="科室名称"]')

    expect(name.attributes('required')).toBeDefined()
    expect(name.attributes('aria-required')).toBe('true')
  })

  it('keeps a pending save locked through real close and parent close/reopen', async () => {
    vi.useFakeTimers()
    let finishSave!: (value: Department) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = mount(defineComponent({
      components: { DepartmentFormDrawer },
      setup() {
        const open = ref(true)
        return { open, close: () => { open.value = false }, reopen: () => { open.value = true } }
      },
      template: `
        <DepartmentFormDrawer v-model="open" />
        <button class="parent-close" @click="close">父级关闭</button>
        <button class="parent-reopen" @click="reopen">重新打开</button>
      `
    }), { attachTo: document.body, global: { stubs: { teleport: true } } })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(DepartmentFormDrawer)
    await fillDrawer(drawer, '儿科', '儿童诊疗')
    const form = drawer.get('form')
    await form.trigger('submit')
    await flushPromises()

    const closeButton = document.querySelector<HTMLButtonElement>('.el-drawer__close-btn')
    expect(closeButton).not.toBeNull()
    closeButton!.click()
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(true)

    await wrapper.get('.parent-close').trigger('click')
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(false)
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="科室名称"]').element as HTMLInputElement).value).toBe('儿科')
    expect(drawer.get('input[aria-label="科室名称"]').attributes('disabled')).toBeDefined()
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    await form.trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(1)

    finishSave(pediatrics)
    await flushPromises()
    expect(drawer.emitted('saved')).toEqual([[pediatrics]])
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="科室名称"]').element as HTMLInputElement).value).toBe('')
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('reopens a fresh usable form when a pending save resolves after parent close', async () => {
    let finishSave!: (value: Department) => void
    vi.mocked(http.post).mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve }))
    const wrapper = mount(defineComponent({
      components: { DepartmentFormDrawer },
      setup() {
        const open = ref(true)
        return { open, close: () => { open.value = false }, reopen: () => { open.value = true } }
      },
      template: `
        <DepartmentFormDrawer v-model="open" />
        <button class="parent-close" @click="close">父级关闭</button>
        <button class="parent-reopen" @click="reopen">重新打开</button>
      `
    }), { attachTo: document.body, global: { stubs: { teleport: true } } })
    await nextTick()
    await flushPromises()
    const drawer = wrapper.findComponent(DepartmentFormDrawer)
    await fillDrawer(drawer, '儿科', '儿童诊疗')
    await drawer.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('.parent-close').trigger('click')
    await flushPromises()
    expect(drawer.props('modelValue')).toBe(false)

    finishSave(pediatrics)
    await flushPromises()
    expect(drawer.emitted('update:modelValue')).toEqual([[false]])
    expect(drawer.emitted('saved')).toEqual([[pediatrics]])

    await wrapper.get('.parent-reopen').trigger('click')
    await flushPromises()
    expect((drawer.get('input[aria-label="科室名称"]').element as HTMLInputElement).value).toBe('')
    expect(drawer.get('input[aria-label="科室名称"]').attributes('disabled')).toBeUndefined()
    expect(drawer.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await fillDrawer(drawer, '口腔科')
    await drawer.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    expect(http.post).toHaveBeenLastCalledWith('/admin/department', { name: '口腔科', description: '' })
    wrapper.unmount()
  })

  it('shows the direct duplicate-name backend error and allows correction', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('科室名称已存在'))
    const wrapper = await mountDrawer()
    await fillDrawer(wrapper, '儿科')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.department-error').text()).toContain('科室名称已存在')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await fillDrawer(wrapper, '小儿科')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('saved')).toEqual([[pediatrics]])
  })
})

describe('DepartmentManageView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue(pageResult([pediatrics, internal, dental]))
    vi.mocked(http.post).mockResolvedValue(pediatrics)
    vi.mocked(http.put).mockResolvedValue(pediatrics)
    vi.mocked(http.delete).mockResolvedValue(undefined)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('loads page one with size ten and renders department columns and details', async () => {
    const wrapper = await mountDepartments()

    expect(http.get).toHaveBeenCalledWith('/admin/department/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(departmentRows(wrapper)).toHaveLength(3)
    expect(departmentRows(wrapper)[0].text()).toContain('3')
    expect(departmentRows(wrapper)[0].text()).toContain('儿科')
    expect(departmentRows(wrapper)[0].text()).toContain('儿童诊疗')
    expect(departmentRows(wrapper)[1].text()).toContain('成人内科诊疗')
    expect(departmentRows(wrapper)[2].text()).toContain('—')
  })

  it('paginates server results with ten records per page', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 11))
      .mockResolvedValueOnce(pageResult([internal], 11))
    const wrapper = await mountDepartments()

    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeDefined()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()

    expect(http.get).toHaveBeenNthCalledWith(2, '/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(departmentRows(wrapper)).toHaveLength(1)
    expect(departmentRows(wrapper)[0].text()).toContain('内科')
    expect(buttonWithText(wrapper, '下一页')!.attributes('disabled')).toBeDefined()
    expect(buttonWithText(wrapper, '上一页')!.attributes('disabled')).toBeUndefined()
  })

  it('filters only current-page names on Enter and restores all rows when cleared', async () => {
    const wrapper = await mountDepartments()
    const input = wrapper.get('input')
    await input.setValue(' 内 ')
    expect(departmentRows(wrapper)).toHaveLength(3)

    await input.trigger('keyup.enter')
    await flushPromises()
    expect(departmentRows(wrapper)).toHaveLength(1)
    expect(departmentRows(wrapper)[0].text()).toContain('内科')
    expect(http.get).toHaveBeenCalledTimes(1)

    await input.setValue('')
    await flushPromises()
    expect(departmentRows(wrapper)).toHaveLength(3)
    expect(wrapper.findComponent(SearchBar).props('modelValue')).toBe('')
  })

  it('applies a trimmed name filter from the query button without a server request', async () => {
    const wrapper = await mountDepartments()
    const search = wrapper.findComponent(SearchBar)
    search.vm.$emit('update:modelValue', ' 口 ')
    await nextTick()
    await search.get('button').trigger('click')
    await flushPromises()

    expect(departmentRows(wrapper)).toHaveLength(1)
    expect(departmentRows(wrapper)[0].text()).toContain('口腔科')
    expect(http.get).toHaveBeenCalledTimes(1)
  })

  it('creates a department and refreshes the current page', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 11))
      .mockResolvedValueOnce(pageResult([internal], 11))
      .mockResolvedValueOnce(pageResult([dental], 11))
    const wrapper = await mountDepartments()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    const drawer = await startCreate(wrapper)
    await fillDrawer(drawer, ' 口腔科 ', ' 口腔健康 ')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/admin/department', { name: '口腔科', description: '口腔健康' })
    expect(http.get).toHaveBeenLastCalledWith('/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(wrapper.findComponent(DepartmentFormDrawer).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('新增成功')
  })

  it('edits a department and refreshes the current page', async () => {
    const wrapper = await mountDepartments()
    const drawer = await startEdit(wrapper)
    await fillDrawer(drawer, ' 儿科门诊 ', ' 更新后的简介 ')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/admin/department', { id: 3, name: '儿科门诊', description: '更新后的简介' })
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent(DepartmentFormDrawer).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain('编辑成功')
  })

  it('requires confirmation with the department name and supports cancellation', async () => {
    const wrapper = await mountDepartments()
    const row = departmentRows(wrapper)[0]
    await row.get('button[aria-label="删除科室 儿科"]').trigger('click')

    const dialog = wrapper.findComponent(ConfirmDialog)
    expect(dialog.props('modelValue')).toBe(true)
    expect(dialog.props('message')).toContain('儿科')
    expect(http.delete).not.toHaveBeenCalled()

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(http.delete).not.toHaveBeenCalled()
    expect(http.get).toHaveBeenCalledTimes(1)
  })

  it('deletes the exact id, closes confirmation, and refreshes after success', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics, internal]))
      .mockResolvedValueOnce(pageResult([internal]))
    const wrapper = await mountDepartments()
    await confirmDelete(wrapper)

    expect(http.delete).toHaveBeenCalledWith('/admin/department/3')
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
    expect(departmentRows(wrapper)[0].text()).toContain('内科')
  })

  it('guards duplicate deletes and keeps the pending target until settlement', async () => {
    let finishDelete!: () => void
    vi.mocked(http.delete).mockReturnValueOnce(new Promise<void>((resolve) => { finishDelete = resolve }))
    const wrapper = await mountDepartments()
    await departmentRows(wrapper)[0].get('button[aria-label="删除科室 儿科"]').trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('update:modelValue', false)
    await flushPromises()

    expect(http.delete).toHaveBeenCalledTimes(1)
    expect(departmentRows(wrapper)[0].get('button[aria-label="删除科室 儿科"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)

    finishDelete()
    await flushPromises()
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(departmentRows(wrapper)[0].get('button[aria-label="删除科室 儿科"]').attributes('disabled')).toBeUndefined()
  })

  it('closes confirmation and prominently shows the exact direct delete rejection reason', async () => {
    vi.mocked(http.delete).mockRejectedValueOnce(new Error('科室下存在医生或排班，无法删除'))
    const wrapper = await mountDepartments()
    await confirmDelete(wrapper)

    expect(wrapper.findComponent(ConfirmDialog).props('modelValue')).toBe(false)
    expect(wrapper.get('.action-error').text()).toContain('科室下存在医生或排班，无法删除')
    expect(wrapper.text()).not.toContain('删除失败，请稍后重试')
    expect(wrapper.find('.action-success').exists()).toBe(false)
    expect(http.get).toHaveBeenCalledTimes(1)
    expect(departmentRows(wrapper)).toHaveLength(3)
  })

  it('moves back to the last valid page after deleting the only row on the last page', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 11))
      .mockResolvedValueOnce(pageResult([internal], 11))
      .mockResolvedValueOnce(pageResult([], 10))
      .mockResolvedValueOnce(pageResult([pediatrics], 10))
    const wrapper = await mountDepartments()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, internal)

    expect(http.delete).toHaveBeenCalledWith('/admin/department/7')
    expect(http.get).toHaveBeenNthCalledWith(3, '/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(http.get).toHaveBeenLastCalledWith('/admin/department/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(wrapper.text()).toContain('第 1 / 1 页，共 10 条')
    expect(wrapper.get('.action-success').text()).toContain('删除成功')
  })

  it('recovers from a concurrent total that still leaves the requested page out of range', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 11))
      .mockResolvedValueOnce(pageResult([internal], 11))
      .mockResolvedValueOnce(pageResult([], 9))
      .mockResolvedValueOnce(pageResult([pediatrics], 9))
    const wrapper = await mountDepartments()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, internal)

    expect(http.get).toHaveBeenNthCalledWith(3, '/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(http.get).toHaveBeenNthCalledWith(4, '/admin/department/page', { params: { pageNum: 1, pageSize: 10 } })
    expect(wrapper.text()).toContain('第 1 / 1 页，共 9 条')
  })

  it('keeps the current last page when a concurrent insert makes it valid after deletion', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 11))
      .mockResolvedValueOnce(pageResult([internal], 11))
      .mockResolvedValueOnce(pageResult([dental], 11))
    const wrapper = await mountDepartments()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()
    await confirmDelete(wrapper, internal)

    expect(http.get).toHaveBeenCalledTimes(3)
    expect(http.get).toHaveBeenLastCalledWith('/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(wrapper.text()).toContain('第 2 / 2 页，共 11 条')
    expect(departmentRows(wrapper)[0].text()).toContain('口腔科')
  })

  it.each(['create', 'update', 'delete'] as const)('preserves %s success when only refresh fails and retries separately', async (action) => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics]))
      .mockRejectedValueOnce(new Error('科室列表暂时不可用'))
      .mockResolvedValueOnce(pageResult([pediatrics]))
    const wrapper = await mountDepartments()

    if (action === 'create') {
      const drawer = await startCreate(wrapper)
      await fillDrawer(drawer, '口腔科')
      await drawer.get('form').trigger('submit')
    } else if (action === 'update') {
      const drawer = await startEdit(wrapper)
      await fillDrawer(drawer, '儿科门诊')
      await drawer.get('form').trigger('submit')
    } else {
      await confirmDelete(wrapper)
    }
    await flushPromises()

    const expectedSuccess = action === 'create' ? '新增成功' : action === 'update' ? '编辑成功' : '删除成功'
    expect(wrapper.get('.action-success').text()).toContain(expectedSuccess)
    expect(wrapper.get('.reload-warning').text()).toContain('科室列表暂时不可用')
    expect(wrapper.text()).not.toContain(`${expectedSuccess.slice(0, 2)}失败`)

    await buttonWithText(wrapper, '重新加载')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.reload-warning').exists()).toBe(false)
    expect(wrapper.get('.action-success').text()).toContain(expectedSuccess)
    expect(http.get).toHaveBeenCalledTimes(3)
  })

  it('shows loading, then an empty state', async () => {
    let finishLoad!: (value: PageResult<Department>) => void
    vi.mocked(http.get).mockReturnValueOnce(new Promise((resolve) => { finishLoad = resolve }))
    const wrapper = mount(DepartmentManageView, {
      global: {
        stubs: { teleport: true },
        directives: { loading: ElLoading.directive }
      }
    })
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载科室')

    finishLoad(pageResult([]))
    await flushPromises()
    expect(wrapper.text()).toContain('暂无科室')
  })

  it('shows direct load errors with a working retry', async () => {
    vi.mocked(http.get)
      .mockRejectedValueOnce(new Error('科室加载失败'))
      .mockResolvedValueOnce(pageResult([pediatrics]))
    const wrapper = await mountDepartments()
    expect(wrapper.text()).toContain('科室加载失败')

    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(departmentRows(wrapper)).toHaveLength(1)
    expect(wrapper.text()).not.toContain('科室加载失败')
  })

  it('replaces stale page-one rows with a direct page-two error and retries page two', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce(pageResult([pediatrics], 21))
      .mockRejectedValueOnce(new Error('第二页加载失败'))
      .mockResolvedValueOnce(pageResult([internal], 21))
    const wrapper = await mountDepartments()
    await buttonWithText(wrapper, '下一页')!.trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(ErrorState).exists()).toBe(true)
    expect(wrapper.findComponent(ErrorState).props('message')).toBe('第二页加载失败')
    expect(wrapper.text()).toContain('第二页加载失败')
    expect(departmentRows(wrapper)).toHaveLength(0)
    expect(wrapper.text()).not.toContain('儿童诊疗')
    expect(wrapper.text()).toContain('第 2 / 3 页，共 21 条')
    expect(http.get).toHaveBeenNthCalledWith(2, '/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })

    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenNthCalledWith(3, '/admin/department/page', { params: { pageNum: 2, pageSize: 10 } })
    expect(departmentRows(wrapper)).toHaveLength(1)
    expect(departmentRows(wrapper)[0].text()).toContain('内科')
    expect(wrapper.text()).not.toContain('第二页加载失败')
    expect(wrapper.text()).toContain('第 2 / 3 页，共 21 条')
  })

  it('announces mutation success without relying on a transient message', async () => {
    const wrapper = await mountDepartments()
    await confirmDelete(wrapper)
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
    expect(wrapper.get('.action-success').attributes('role')).toBe('status')
  })

  it('keeps the admin role guard and exposes the real lazy department component', async () => {
    const matched = router.resolve('/admin/departments')!
    expect(matched.meta.roles).toEqual([1])
    expect(matched.meta.requiresAuth).toBe(true)
    const loader = matched.matched.at(-1)!.components!.default!
    expect(typeof loader).toBe('function')
    const component = await (loader as () => Promise<{ default: unknown }>)()
    expect(component.default).toBe(DepartmentManageView)
  })
})
