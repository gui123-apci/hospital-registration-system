import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, type Component } from 'vue'
import { http } from '@/api/http'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PatientFormDrawer from '@/components/PatientFormDrawer.vue'
import PatientListView from '@/views/patient/PatientListView.vue'
import ReportListView from '@/views/patient/ReportListView.vue'
import TriageView from '@/views/patient/TriageView.vue'
import { relationLabel, relationOptions } from '@/utils/patient'
import type { Patient, Report, TriageResult } from '@/types/models'

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

const patient: Patient = {
  id: 17,
  name: '李患者',
  relation: 1,
  phone: '13800000000',
  idCard: '11010519491231002X'
}

const createdPatient: Patient = {
  id: 18,
  name: '赵患者',
  relation: 2,
  phone: '13700000000',
  idCard: '110105194912310033'
}

const updatedPatient: Patient = {
  ...patient,
  name: '王患者',
  relation: 3,
  phone: '13900000000',
  idCard: '110105194912310044'
}

const report: Report = {
  id: 5,
  patientId: 17,
  patientName: '李患者',
  orderId: 99,
  reportType: '血常规',
  title: '血常规检验报告',
  content: '白细胞计数 6.2 ×10⁹/L\n血红蛋白 132 g/L，未见明显异常。',
  reportTime: '2026-11-25T09:30:00'
}

const recommendation: TriageResult = {
  answer: '建议挂呼吸内科。咳嗽、发热常见于呼吸道疾病，可以先到呼吸内科评估。',
  schedules: []
}

function mountPage(component: Component) {
  return mount(component, {
    global: {
      stubs: {
        teleport: true,
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }
      }
    }
  })
}

function buttonWithText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((button) => button.text().trim() === text)
}

async function openPatientDrawer(wrapper: VueWrapper, action: '新增就诊人' | '编辑') {
  await buttonWithText(wrapper, action)!.trigger('click')
  await nextTick()
  await flushPromises()
}

async function fillPatientForm(
  wrapper: VueWrapper,
  values: { name: string; relation: number | ''; phone?: string; idCard?: string }
) {
  await wrapper.get('input[name="name"]').setValue(values.name)
  await wrapper.get('select[name="relation"]').setValue(String(values.relation))
  if (values.phone !== undefined) await wrapper.get('input[name="phone"]').setValue(values.phone)
  if (values.idCard !== undefined) await wrapper.get('input[name="idCard"]').setValue(values.idCard)
}

describe('patient relation and privacy rules', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue([patient])
    vi.mocked(http.post).mockResolvedValue(createdPatient)
    vi.mocked(http.put).mockResolvedValue(updatedPatient)
    vi.mocked(http.delete).mockResolvedValue(undefined)
  })

  it('maps the backend relation codes to authoritative Chinese labels', () => {
    expect(relationOptions).toEqual([
      { label: '本人', value: 0 },
      { label: '父母', value: 1 },
      { label: '子女', value: 2 },
      { label: '其他', value: 3 }
    ])
    expect(relationLabel(0)).toBe('本人')
    expect(relationLabel(1)).toBe('父母')
    expect(relationLabel(2)).toBe('子女')
    expect(relationLabel(3)).toBe('其他')
    expect(relationLabel(99)).toBe('未知关系')
  })

  it('renders Chinese relation labels, masks identity data, and offers edit and delete actions', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()

    expect(http.get).toHaveBeenCalledWith('/patient/list')
    expect(wrapper.get('.patient-name').text()).toBe('李患者')
    expect(wrapper.get('.relation-tag').text()).toBe('父母')
    expect(wrapper.text()).toContain('110105********002X')
    expect(wrapper.text()).not.toContain('11010519491231002X')
    expect(buttonWithText(wrapper, '编辑')).toBeDefined()
    expect(buttonWithText(wrapper, '删除')).toBeDefined()
  })

  it('deletes the confirmed patient through the id path and refreshes the list', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce([patient])
      .mockResolvedValueOnce([])
    const wrapper = mountPage(PatientListView)
    await flushPromises()

    await buttonWithText(wrapper, '删除')!.trigger('click')
    const dialog = wrapper.findComponent(ConfirmDialog)
    expect(dialog.props('modelValue')).toBe(true)
    expect(dialog.props('message')).toContain('李患者')
    expect(http.delete).not.toHaveBeenCalled()

    dialog.vm.$emit('confirm')
    await flushPromises()

    expect(http.delete).toHaveBeenCalledTimes(1)
    expect(http.delete).toHaveBeenCalledWith('/patient/17')
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.success-alert').text()).toContain('已删除就诊人 李患者')
    expect(wrapper.text()).toContain('暂无就诊人')
  })

  it('displays backend delete errors and keeps the patient in the list', async () => {
    vi.mocked(http.delete).mockRejectedValueOnce(new Error('该就诊人存在挂号记录，无法删除'))
    const wrapper = mountPage(PatientListView)
    await flushPromises()

    await buttonWithText(wrapper, '删除')!.trigger('click')
    wrapper.findComponent(ConfirmDialog).vm.$emit('confirm')
    await flushPromises()

    expect(http.delete).toHaveBeenCalledWith('/patient/17')
    expect(http.get).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.el-alert--error').text()).toContain('该就诊人存在挂号记录，无法删除')
    expect(wrapper.get('.patient-name').text()).toBe('李患者')
  })

  it('shows Chinese relation choices and numeric edit values in the form drawer', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '编辑')

    const options = wrapper.findAll('select[name="relation"] option')
    expect(options.map((option) => option.text())).toEqual(['请选择关系', '本人', '父母', '子女', '其他'])
    expect((wrapper.get('select[name="relation"]').element as HTMLSelectElement).value).toBe('1')
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe('李患者')
  })

  it('creates without an id using the exact payload and refreshes after success', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '新增就诊人')
    await fillPatientForm(wrapper, {
      name: '赵患者',
      relation: 2,
      phone: '13700000000',
      idCard: '110105194912310033'
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/patient', {
      name: '赵患者',
      relation: 2,
      phone: '13700000000',
      idCard: '110105194912310033'
    })
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent(PatientFormDrawer).props('modelValue')).toBe(false)
  })

  it('updates the selected patient with its id and refreshes after success', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce([patient])
      .mockResolvedValueOnce([updatedPatient])
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '编辑')
    await fillPatientForm(wrapper, {
      name: '王患者',
      relation: 3,
      phone: '13900000000',
      idCard: '110105194912310044'
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).toHaveBeenCalledTimes(1)
    expect(http.put).toHaveBeenCalledWith('/patient', {
      id: 17,
      name: '王患者',
      relation: 3,
      phone: '13900000000',
      idCard: '110105194912310044'
    })
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('王患者')
    expect(wrapper.text()).toContain('其他')
    expect(wrapper.findComponent(PatientFormDrawer).props('modelValue')).toBe(false)
  })

  it('prevents invalid patient requests and identifies each invalid field', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '新增就诊人')
    await fillPatientForm(wrapper, {
      name: '',
      relation: '',
      phone: '12800000000',
      idCard: '123'
    })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入姓名')
    expect(wrapper.text()).toContain('请选择关系')
    expect(wrapper.text()).toContain('请输入正确的手机号')
    expect(wrapper.text()).toContain('请输入正确的身份证号')
  })

  it('accepts blank optional fields and omits them from a create payload', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '新增就诊人')
    await fillPatientForm(wrapper, { name: '赵患者', relation: 3 })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/patient', { name: '赵患者', relation: 3 })
  })

  it('uses empty strings to clear existing optional fields during an update', async () => {
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '编辑')
    await wrapper.get('input[name="phone"]').setValue('')
    await wrapper.get('input[name="idCard"]').setValue('')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/patient', {
      id: 17,
      name: '李患者',
      relation: 1,
      phone: '',
      idCard: ''
    })
  })

  it('prevents duplicate patient creation while the request is pending', async () => {
    let finishCreate!: (value: Patient) => void
    vi.mocked(http.post).mockReturnValue(new Promise((resolve) => {
      finishCreate = resolve
    }))
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '新增就诊人')
    await fillPatientForm(wrapper, { name: '赵患者', relation: 2 })

    await wrapper.get('form').trigger('submit')
    expect(buttonWithText(wrapper, '保存中…')!.attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)

    finishCreate(createdPatient)
    await flushPromises()
  })

  it('displays backend create errors directly and keeps the form open', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('该就诊人已存在'))
    const wrapper = mountPage(PatientListView)
    await flushPromises()
    await openPatientDrawer(wrapper, '新增就诊人')
    await fillPatientForm(wrapper, { name: '赵患者', relation: 2 })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.form-error').text()).toContain('该就诊人已存在')
    expect(http.get).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent(PatientFormDrawer).props('modelValue')).toBe(true)
  })

  it('shows patient load errors and retries the list', async () => {
    vi.mocked(http.get)
      .mockRejectedValueOnce(new Error('就诊人加载失败'))
      .mockResolvedValueOnce([patient])
    const wrapper = mountPage(PatientListView)
    await flushPromises()

    expect(wrapper.text()).toContain('就诊人加载失败')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.patient-name').text()).toBe('李患者')
  })
})

describe('PatientFormDrawer', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.post).mockResolvedValue(createdPatient)
    vi.mocked(http.put).mockResolvedValue(updatedPatient)
  })

  function mountPatientDrawer(patientData: Patient | null = patient) {
    return mount(PatientFormDrawer, {
      props: { modelValue: true, patient: patientData },
      global: {
        stubs: {
          teleport: true,
          FormDrawer: { props: ['modelValue'], template: '<div><slot /></div>' }
        }
      }
    })
  }

  it('updates an unrelated field without resubmitting an unchanged masked id card', async () => {
    const wrapper = mountPatientDrawer({ ...patient, idCard: '110105********002X' })
    await wrapper.get('input[name="name"]').setValue('王患者')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/patient', {
      id: 17,
      name: '王患者',
      relation: 1,
      phone: '13800000000'
    })
    expect(vi.mocked(http.put).mock.calls[0][1]).not.toHaveProperty('idCard')
    expect(wrapper.emitted('saved')).toEqual([[updatedPatient]])
  })

  it('rejects a modified masked id card before calling the API', async () => {
    const wrapper = mountPatientDrawer({ ...patient, idCard: '110105********002X' })
    await wrapper.get('input[name="idCard"]').setValue('110105********003X')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.put).not.toHaveBeenCalled()
    expect(wrapper.get('#patient-id-card-error').text()).toContain('请输入正确的身份证号')
    expect(wrapper.get('input[name="idCard"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('accepts and trims a 32-character name before creating a patient', async () => {
    const wrapper = mountPatientDrawer(null)
    const name = '姓'.repeat(32)
    expect(wrapper.get('input[name="name"]').attributes('maxlength')).toBe('32')
    await wrapper.get('input[name="name"]').setValue(`  ${name}  `)
    await wrapper.get('select[name="relation"]').setValue('2')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/patient', { name, relation: 2 })
  })

  it('rejects a trimmed 33-character name before calling the API', async () => {
    const wrapper = mountPatientDrawer(null)
    await wrapper.get('input[name="name"]').setValue(`  ${'姓'.repeat(33)}  `)
    await wrapper.get('select[name="relation"]').setValue('2')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalled()
    expect(http.put).not.toHaveBeenCalled()
    expect(wrapper.get('#patient-name-error').text()).toContain('姓名不能超过 32 个字')
    expect(wrapper.get('input[name="name"]').attributes('aria-invalid')).toBe('true')
  })
})

describe('ReportListView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.get).mockResolvedValue([report])
  })

  it('renders report metadata and expands then collapses the full content accessibly', async () => {
    const wrapper = mountPage(ReportListView)
    await flushPromises()

    expect(http.get).toHaveBeenCalledWith('/report/list')
    expect(wrapper.text()).toContain('李患者')
    expect(wrapper.text()).toContain('血常规')
    expect(wrapper.text()).toContain('血常规检验报告')
    expect(wrapper.text()).toContain('2026-11-25 09:30')
    expect(wrapper.text()).not.toContain('白细胞计数')

    const toggle = buttonWithText(wrapper, '查看全文')!
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.attributes('aria-controls')).toBe('report-content-5')
    await toggle.trigger('click')

    expect(wrapper.get('#report-content-5').text()).toContain('白细胞计数 6.2 ×10⁹/L')
    expect(buttonWithText(wrapper, '收起全文')!.attributes('aria-expanded')).toBe('true')

    await buttonWithText(wrapper, '收起全文')!.trigger('click')
    expect(wrapper.find('#report-content-5').exists()).toBe(false)
  })

  it('renders optional report metadata and content fallbacks', async () => {
    vi.mocked(http.get).mockResolvedValue([{
      ...report,
      reportType: undefined,
      reportTime: undefined,
      content: undefined
    }])
    const wrapper = mountPage(ReportListView)
    await flushPromises()

    expect(wrapper.text()).toContain('未分类')
    expect(wrapper.text()).toContain('—')
    await buttonWithText(wrapper, '查看全文')!.trigger('click')
    expect(wrapper.get('#report-content-5').text()).toContain('暂无可查看的报告内容')
  })

  it('shows an empty state when there are no reports', async () => {
    vi.mocked(http.get).mockResolvedValue([])
    const wrapper = mountPage(ReportListView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无报告')
    expect(wrapper.findAll('article')).toHaveLength(0)
  })

  it('shows report load errors and retries successfully', async () => {
    vi.mocked(http.get)
      .mockRejectedValueOnce(new Error('报告加载失败'))
      .mockResolvedValueOnce([report])
    const wrapper = mountPage(ReportListView)
    await flushPromises()

    expect(wrapper.text()).toContain('报告加载失败')
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('血常规检验报告')
  })
})

describe('TriageView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(http.post).mockResolvedValue(recommendation)
  })

  it('enforces 500 characters and sends exactly the symptoms payload', async () => {
    const wrapper = mountPage(TriageView)
    const textarea = wrapper.get('textarea')
    const submit = wrapper.get('form')

    expect(textarea.attributes('maxlength')).toBe('500')
    await textarea.setValue('症'.repeat(501))
    expect(wrapper.get('.character-count').text()).toBe('501 / 500')
    await submit.trigger('submit')
    await flushPromises()
    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('症状描述不能超过500字')

    const symptoms = '症'.repeat(500)
    await textarea.setValue(symptoms)
    await submit.trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/triage/ask', { symptoms })
  })

  it('requires a nonblank symptom description', async () => {
    const wrapper = mountPage(TriageView)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请描述您的症状')
  })

  it('does not reference an accessible error when symptoms are error-free', () => {
    const wrapper = mountPage(TriageView)
    const textarea = wrapper.get('textarea')

    expect(wrapper.find('#symptom-error').exists()).toBe(false)
    expect(textarea.attributes('aria-describedby')).toBe('symptom-hint character-count')
    expect(textarea.attributes('aria-invalid')).not.toBe('true')
  })

  it('associates an invalid symptom description with its accessible error', async () => {
    const wrapper = mountPage(TriageView)
    const textarea = wrapper.get('textarea')
    expect(textarea.attributes('aria-invalid')).not.toBe('true')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const error = wrapper.get('.triage-error')
    const errorId = error.attributes('id')
    expect(errorId).toBe('symptom-error')
    expect(error.attributes('role')).toBe('alert')
    expect(textarea.attributes('aria-describedby')?.split(/\s+/)).toEqual(expect.arrayContaining([
      'symptom-hint',
      'character-count',
      errorId
    ]))
    expect(textarea.attributes('aria-invalid')).toBe('true')
  })

  it('clears a completed answer when symptoms change', async () => {
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('咳嗽、发热两天')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.triage-result').text()).toContain(recommendation.answer)

    await wrapper.get('textarea').setValue('咳嗽三天，没有发热')

    expect(wrapper.find('.triage-result').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(recommendation.answer)
  })

  it('trims the symptoms before sending them to the backend', async () => {
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('  头痛、发热两天  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/triage/ask', { symptoms: '头痛、发热两天' })
  })

  it('clears a prior submit validation error when symptoms change', async () => {
    const wrapper = mountPage(TriageView)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.triage-error').text()).toContain('请描述您的症状')

    await wrapper.get('textarea').setValue('头痛')

    expect(wrapper.find('.triage-error').exists()).toBe(false)
    expect(wrapper.get('textarea').attributes('aria-invalid')).not.toBe('true')
  })

  it('clears a prior backend error when symptoms change', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new Error('导诊服务暂时不可用'))
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('头痛')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.triage-error').text()).toContain('导诊服务暂时不可用')

    await wrapper.get('textarea').setValue('咳嗽')

    expect(wrapper.find('.triage-error').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('导诊服务暂时不可用')
    expect(wrapper.get('textarea').attributes('aria-invalid')).not.toBe('true')
  })

  it('disables duplicate submissions while triage is loading', async () => {
    let finishTriage!: (value: TriageResult) => void
    vi.mocked(http.post).mockReturnValue(new Promise((resolve) => {
      finishTriage = resolve
    }))
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('头痛、发热两天')

    await wrapper.get('form').trigger('submit')
    const submitButton = wrapper.get('button[type="submit"]')
    expect(submitButton.attributes('disabled')).toBeDefined()
    expect(submitButton.text()).toContain('分析中')
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)

    finishTriage(recommendation)
    await flushPromises()
    expect(wrapper.get('.triage-result').text()).toContain(recommendation.answer)
  })

  it('renders the backend answer as plain text in an accessible result region', async () => {
    vi.mocked(http.post).mockResolvedValue({
      answer: '建议先到呼吸内科。\n<strong>请勿自行用药</strong><img src=x onerror="alert(1)">'
    })
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('咳嗽、发热两天')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const result = wrapper.get('.triage-result')
    expect(result.attributes('role')).toBe('region')
    expect(result.attributes('aria-label')).toBe('导诊结果')
    expect(result.attributes('aria-live')).toBe('polite')
    expect(result.get('.triage-answer').text()).toBe('建议先到呼吸内科。\n<strong>请勿自行用药</strong><img src=x onerror="alert(1)">')
    expect(result.find('strong').exists()).toBe(false)
    expect(result.find('img').exists()).toBe(false)
  })

  it('displays backend triage errors directly and permits another attempt', async () => {
    vi.mocked(http.post)
      .mockRejectedValueOnce(new Error('导诊服务暂时不可用'))
      .mockResolvedValueOnce(recommendation)
    const wrapper = mountPage(TriageView)
    await wrapper.get('textarea').setValue('头痛')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.triage-error').text()).toContain('导诊服务暂时不可用')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.triage-error').exists()).toBe(false)
    expect(wrapper.get('.triage-result').text()).toContain(recommendation.answer)
  })
})
