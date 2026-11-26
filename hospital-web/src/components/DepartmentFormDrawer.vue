<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { adminDepartmentApi, type DepartmentCreateRequest } from '@/api/adminDepartment'
import FormDrawer from '@/components/FormDrawer.vue'
import type { Department } from '@/types/models'

const props = defineProps<{
  modelValue: boolean
  department?: Department | null
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [department: Department]
}>()

const form = reactive({ name: '', description: '' })
const errors = reactive({ name: '', description: '' })
const submitError = ref('')
const submitting = ref(false)
const saved = ref(false)
const editing = computed(() => Boolean(props.department))
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    if (value || !submitting.value) emit('update:modelValue', value)
  }
})

function resetForm() {
  form.name = props.department?.name ?? ''
  form.description = props.department?.description ?? ''
  errors.name = ''
  errors.description = ''
  submitError.value = ''
}

function validate() {
  const name = form.name.trim()
  const description = form.description.trim()
  errors.name = !name ? '请输入科室名称' : name.length > 32 ? '科室名称不能超过 32 个字' : ''
  errors.description = description.length > 255 ? '科室简介不能超过 255 个字' : ''
  return !errors.name && !errors.description
}

function handleBeforeClose(done: () => void) {
  if (!submitting.value) done()
}

async function submit() {
  if (submitting.value || saved.value || !props.modelValue) return
  submitError.value = ''
  if (!validate()) return

  submitting.value = true
  try {
    const payload: DepartmentCreateRequest = {
      name: form.name.trim(),
      description: form.description.trim()
    }
    const department = props.department
      ? await adminDepartmentApi.update({ id: props.department.id, ...payload })
      : await adminDepartmentApi.create(payload)
    saved.value = true
    emit('update:modelValue', false)
    emit('saved', department)
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '科室保存失败，请稍后重试'
  } finally {
    submitting.value = false
    if (!props.modelValue) saved.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (open) {
    if (!submitting.value && !saved.value) resetForm()
  } else if (!submitting.value) {
    saved.value = false
  }
}, { immediate: true })

watch(() => props.department, () => {
  if (props.modelValue && !submitting.value && !saved.value) resetForm()
})
</script>

<template>
  <FormDrawer
    v-model="visible"
    :title="editing ? '编辑科室' : '新增科室'"
    size="min(520px, 100vw)"
    :before-close="handleBeforeClose"
  >
    <form class="department-form" novalidate @submit.prevent="submit">
      <p class="form-intro">科室将用于医生管理、排班和患者挂号，请保持名称清晰唯一。</p>

      <ElAlert
        v-if="submitError"
        class="department-error"
        type="error"
        :title="submitError"
        show-icon
        :closable="false"
      />

      <div class="form-field">
        <label for="department-name">科室名称 <span aria-hidden="true">*</span></label>
        <ElInput
          id="department-name"
          v-model="form.name"
          aria-label="科室名称"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.name)"
          :aria-describedby="errors.name ? 'department-name-error' : undefined"
          :maxlength="32"
          :disabled="submitting || saved"
          placeholder="请输入科室名称"
          @input="submitError = ''"
        />
        <p v-if="errors.name" id="department-name-error" class="field-error" role="alert">{{ errors.name }}</p>
      </div>

      <div class="form-field">
        <label for="department-description">科室简介 <span class="optional-label">选填</span></label>
        <ElInput
          id="department-description"
          v-model="form.description"
          aria-label="科室简介"
          type="textarea"
          :rows="5"
          :maxlength="255"
          :aria-invalid="Boolean(errors.description)"
          :aria-describedby="errors.description ? 'department-description-error' : undefined"
          :disabled="submitting || saved"
          placeholder="介绍科室的诊疗范围或服务内容"
          @input="submitError = ''"
        />
        <p v-if="errors.description" id="department-description-error" class="field-error" role="alert">{{ errors.description }}</p>
      </div>

      <div class="form-actions">
        <ElButton :disabled="submitting || saved" @click="visible = false">取消</ElButton>
        <ElButton
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="submitting || saved"
        >
          {{ submitting ? '正在保存…' : editing ? '保存修改' : '新增科室' }}
        </ElButton>
      </div>
    </form>
  </FormDrawer>
</template>

<style scoped>
.department-form { display: grid; gap: 18px; padding: 4px 0 24px; }
.form-intro { margin: 0; color: var(--color-muted); font-size: 13px; line-height: 1.7; }
.department-error { margin: 0; }
.form-field { display: grid; gap: 8px; }
.form-field label { color: var(--color-text); font-size: 14px; font-weight: 700; }
.form-field label > span:not(.optional-label) { color: #b42318; }
.optional-label { margin-left: 4px; color: var(--color-muted); font-size: 12px; font-weight: 400; }
.field-error { margin: 0; color: #b42318; font-size: 12px; line-height: 1.5; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.form-actions :deep(.el-button) { min-height: 44px; margin-left: 0; border-radius: 10px; font-weight: 700; }
.department-form :deep(.el-input__wrapper:focus-within),
.department-form :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 3px rgba(8, 127, 122, .16); }
.department-form :deep(.el-input.is-invalid .el-input__wrapper),
.department-form :deep(.el-textarea.is-invalid .el-textarea__inner) { box-shadow: 0 0 0 1px #b42318; }
@media (max-width: 520px) {
  .department-form { padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
  .form-actions { display: grid; grid-template-columns: 1fr 1.5fr; }
}
</style>
