<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { adminDepartmentApi } from '@/api/adminDepartment'
import { adminDoctorApi } from '@/api/adminDoctor'
import FormDrawer from '@/components/FormDrawer.vue'
import NameIdSelect, { type NameIdOption } from '@/components/NameIdSelect.vue'
import type { Doctor } from '@/types/models'

const props = defineProps<{
  modelValue: boolean
  doctor?: Doctor | null
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [doctor: Doctor]
}>()

const form = reactive<{ departmentId?: number; name: string; phone: string; title: string; intro: string }>({
  departmentId: undefined,
  name: '',
  phone: '',
  title: '',
  intro: ''
})
const errors = reactive({ departmentId: '', name: '', phone: '', title: '', intro: '' })
const options = ref<NameIdOption[]>([])
const optionsLoading = ref(false)
const optionsError = ref('')
const submitError = ref('')
const submitting = ref(false)
const saved = ref(false)
let optionsRequest = 0
const editing = computed(() => Boolean(props.doctor))
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    if (value || !submitting.value) emit('update:modelValue', value)
  }
})

function resetForm() {
  form.departmentId = props.doctor?.departmentId
  form.name = props.doctor?.name ?? ''
  form.phone = ''
  form.title = props.doctor?.title ?? ''
  form.intro = props.doctor?.intro ?? ''
  errors.departmentId = ''
  errors.name = ''
  errors.phone = ''
  errors.title = ''
  errors.intro = ''
  submitError.value = ''
}

async function loadOptions() {
  const request = ++optionsRequest
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const result = await adminDepartmentApi.list()
    if (request !== optionsRequest) return
    options.value = result.map((department) => ({ label: department.name, value: department.id }))
  } catch (reason) {
    if (request !== optionsRequest) return
    options.value = []
    optionsError.value = reason instanceof Error ? reason.message : '科室选项加载失败，请稍后重试'
  } finally {
    if (request === optionsRequest) optionsLoading.value = false
  }
}

function validate() {
  const name = form.name.trim()
  const phone = form.phone.trim()
  const title = form.title.trim()
  const intro = form.intro.trim()
  errors.departmentId = form.departmentId === undefined ? '请选择科室' : ''
  errors.name = !name ? '请输入医生姓名' : name.length > 32 ? '医生姓名不能超过 32 个字' : ''
  // 编辑时不改手机号：账号一旦建立，改手机号涉及登录名变更，放到账号管理中处理
  errors.phone = editing.value && !phone
    ? ''
    : !phone ? '请输入手机号' : /^1[3-9]\d{9}$/.test(phone) ? '' : '请输入 11 位手机号'
  errors.title = title.length > 32 ? '职称不能超过 32 个字' : ''
  errors.intro = intro.length > 255 ? '医生简介不能超过 255 个字' : ''
  return !errors.departmentId && !errors.name && !errors.phone && !errors.title && !errors.intro
}

async function submit() {
  if (submitting.value || saved.value || !props.modelValue || optionsLoading.value || optionsError.value) return
  submitError.value = ''
  if (!validate()) return

  const departmentId = form.departmentId
  if (departmentId === undefined) return
  submitting.value = true
  try {
    const base = {
      departmentId,
      name: form.name.trim(),
      title: form.title.trim(),
      intro: form.intro.trim()
    }
    const doctor = props.doctor
      ? await adminDoctorApi.update({ id: props.doctor.id, ...base })
      : await adminDoctorApi.create({ ...base, phone: form.phone.trim() })
    saved.value = true
    emit('update:modelValue', false)
    emit('saved', doctor)
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '医生保存失败，请稍后重试'
  } finally {
    submitting.value = false
    if (!props.modelValue) saved.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (open) {
    if (!submitting.value && !saved.value) {
      resetForm()
      void loadOptions()
    }
  } else if (!submitting.value) {
    saved.value = false
    optionsRequest += 1
    options.value = []
  }
}, { immediate: true })

watch(() => props.doctor, () => {
  if (props.modelValue && !submitting.value && !saved.value) resetForm()
})
</script>

<template>
  <FormDrawer
    v-model="visible"
    :title="editing ? '编辑医生' : '新增医生'"
    size="min(560px, 100vw)"
  >
    <form class="doctor-form" novalidate @submit.prevent="submit">
      <p class="form-intro">医生将用于患者挂号、排班和诊疗报告，请准确填写所属科室与执业信息。</p>

      <ElAlert
        v-if="submitError"
        class="doctor-error"
        type="error"
        :title="submitError"
        role="alert"
        show-icon
        :closable="false"
      />

      <div class="form-field">
        <label for="doctor-department">所属科室 <span aria-hidden="true">*</span></label>
        <NameIdSelect
          id="doctor-department"
          v-model="form.departmentId"
          name="departmentId"
          class="department-select"
          :class="{ 'is-invalid': Boolean(errors.departmentId) }"
          :options="options"
          placeholder="请选择科室"
          :disabled="submitting || saved || optionsLoading || Boolean(optionsError)"
          aria-label="请选择科室"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.departmentId)"
          :aria-describedby="errors.departmentId ? 'doctor-department-error' : optionsError ? 'doctor-options-error' : undefined"
          @update:model-value="errors.departmentId = ''; submitError = ''"
        />
        <p v-if="optionsLoading" class="options-status" role="status">正在加载科室…</p>
        <div v-if="optionsError" id="doctor-options-error" class="options-error" role="alert">
          <span>{{ optionsError }}</span>
          <ElButton :loading="optionsLoading" @click="loadOptions">重试</ElButton>
        </div>
        <p v-if="errors.departmentId" id="doctor-department-error" class="field-error" role="alert">{{ errors.departmentId }}</p>
      </div>

      <div class="form-field">
        <label for="doctor-name">医生姓名 <span aria-hidden="true">*</span></label>
        <ElInput
          id="doctor-name"
          v-model="form.name"
          aria-label="医生姓名"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.name)"
          :aria-describedby="errors.name ? 'doctor-name-error' : undefined"
          :maxlength="32"
          :disabled="submitting || saved"
          placeholder="请输入医生姓名"
          @input="submitError = ''"
        />
        <p v-if="errors.name" id="doctor-name-error" class="field-error" role="alert">{{ errors.name }}</p>
      </div>

      <div v-if="!editing" class="form-field">
        <label for="doctor-phone">手机号 <span aria-hidden="true">*</span></label>
        <ElInput
          id="doctor-phone"
          v-model="form.phone"
          aria-label="手机号"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.phone)"
          :aria-describedby="errors.phone ? 'doctor-phone-error' : 'doctor-phone-hint'"
          :maxlength="11"
          :disabled="submitting || saved"
          placeholder="请输入 11 位手机号"
          @input="submitError = ''"
        />
        <p v-if="errors.phone" id="doctor-phone-error" class="field-error" role="alert">{{ errors.phone }}</p>
        <p v-else id="doctor-phone-hint" class="field-hint">该手机号同时作为医生登录账号，初始密码为 admin123。</p>
      </div>

      <div class="form-field">
        <label for="doctor-title">职称 <span class="optional-label">选填</span></label>
        <ElInput
          id="doctor-title"
          v-model="form.title"
          aria-label="职称"
          :aria-invalid="Boolean(errors.title)"
          :aria-describedby="errors.title ? 'doctor-title-error' : undefined"
          :maxlength="32"
          :disabled="submitting || saved"
          placeholder="如：主任医师"
          @input="submitError = ''"
        />
        <p v-if="errors.title" id="doctor-title-error" class="field-error" role="alert">{{ errors.title }}</p>
      </div>

      <div class="form-field">
        <label for="doctor-intro">医生简介 <span class="optional-label">选填</span></label>
        <ElInput
          id="doctor-intro"
          v-model="form.intro"
          aria-label="医生简介"
          type="textarea"
          :rows="4"
          :maxlength="255"
          :aria-invalid="Boolean(errors.intro)"
          :aria-describedby="errors.intro ? 'doctor-intro-error' : undefined"
          :disabled="submitting || saved"
          placeholder="介绍医生的诊疗专长或服务方向"
          @input="submitError = ''"
        />
        <p v-if="errors.intro" id="doctor-intro-error" class="field-error" role="alert">{{ errors.intro }}</p>
      </div>

      <div class="form-actions">
        <ElButton :disabled="submitting || saved" @click="visible = false">取消</ElButton>
        <ElButton
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="submitting || saved || optionsLoading || Boolean(optionsError)"
        >
          {{ submitting ? '正在保存…' : editing ? '保存修改' : '新增医生' }}
        </ElButton>
      </div>
    </form>
  </FormDrawer>
</template>

<style scoped>
.doctor-form { display: grid; gap: 18px; padding: 4px 0 24px; }
.form-intro { margin: 0; color: var(--color-muted); font-size: 13px; line-height: 1.7; }
.doctor-error { margin: 0; }
.form-field { display: grid; gap: 8px; }
.form-field label { color: var(--color-text); font-size: 14px; font-weight: 700; }
.form-field label > span:not(.optional-label) { color: #b42318; }
.optional-label { margin-left: 4px; color: var(--color-muted); font-size: 12px; font-weight: 400; }
.department-select { width: 100%; }
.field-error, .options-status, .field-hint { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.5; }
.field-error { color: #b42318; }
.options-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border: 1px solid #f0c9c4; border-radius: 8px; background: #fff5f4; color: #b42318; font-size: 13px; }
.options-error :deep(.el-button) { min-height: 36px; margin-left: 0; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.form-actions :deep(.el-button) { min-height: 44px; margin-left: 0; border-radius: 10px; font-weight: 700; }
.doctor-form :deep(.el-input__wrapper:focus-within),
.doctor-form :deep(.el-textarea__inner:focus),
.department-select :deep(.el-select__wrapper:focus-within) { box-shadow: 0 0 0 3px rgba(8, 127, 122, .16); }
.doctor-form :deep(.el-input.is-invalid .el-input__wrapper),
.doctor-form :deep(.el-textarea.is-invalid .el-textarea__inner),
.department-select.is-invalid :deep(.el-select__wrapper) { box-shadow: 0 0 0 1px #b42318; }
@media (max-width: 520px) {
  .doctor-form { padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
  .form-actions { display: grid; grid-template-columns: 1fr 1.5fr; }
}
</style>
