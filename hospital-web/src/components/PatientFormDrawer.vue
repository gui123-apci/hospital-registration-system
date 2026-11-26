<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { patientApi, type PatientCreateRequest, type PatientUpdateRequest } from '@/api/patient'
import FormDrawer from '@/components/FormDrawer.vue'
import { relationOptions } from '@/utils/patient'
import { isValidIdCard, isValidPhone } from '@/utils/validation'
import type { Patient } from '@/types/models'

const props = defineProps<{
  modelValue: boolean
  patient?: Patient | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [patient: Patient]
}>()

const form = reactive({
  name: '',
  relation: null as number | null,
  phone: '',
  idCard: ''
})
const errors = reactive({
  name: '',
  relation: '',
  phone: '',
  idCard: ''
})
const submitError = ref('')
const submitting = ref(false)
const editing = computed(() => Boolean(props.patient))
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    if (!submitting.value) emit('update:modelValue', value)
  }
})

function resetForm() {
  form.name = props.patient?.name ?? ''
  form.relation = props.patient?.relation ?? null
  form.phone = props.patient?.phone ?? ''
  form.idCard = props.patient?.idCard ?? ''
  errors.name = ''
  errors.relation = ''
  errors.phone = ''
  errors.idCard = ''
  submitError.value = ''
}

function isUnchangedMaskedIdCard(value: string) {
  return editing.value
    && Boolean(props.patient?.idCard?.includes('*'))
    && value === props.patient?.idCard
}

function validate() {
  const name = form.name.trim()
  errors.name = !name ? '请输入姓名' : name.length > 32 ? '姓名不能超过 32 个字' : ''
  errors.relation = typeof form.relation === 'number'
    && relationOptions.some((option) => option.value === form.relation) ? '' : '请选择关系'
  errors.phone = form.phone.trim() && !isValidPhone(form.phone.trim()) ? '请输入正确的手机号' : ''
  errors.idCard = form.idCard.trim()
    && !isUnchangedMaskedIdCard(form.idCard.trim())
    && !isValidIdCard(form.idCard.trim()) ? '请输入正确的身份证号' : ''
  return !errors.name && !errors.relation && !errors.phone && !errors.idCard
}

async function submit() {
  if (submitting.value || !props.modelValue) return
  submitError.value = ''
  if (!validate()) return

  submitting.value = true
  try {
    const name = form.name.trim()
    const phone = form.phone.trim()
    const idCard = form.idCard.trim()
    const payload: PatientCreateRequest = { name, relation: form.relation! }
    if (phone) payload.phone = phone
    if (idCard && !isUnchangedMaskedIdCard(idCard)) payload.idCard = idCard

    let saved: Patient
    if (props.patient) {
      const update: PatientUpdateRequest = { id: props.patient.id, ...payload }
      if (!phone && props.patient.phone !== undefined) update.phone = ''
      if (!idCard && props.patient.idCard !== undefined) update.idCard = ''
      saved = await patientApi.update(update)
    } else {
      saved = await patientApi.create(payload)
    }

    emit('saved', saved)
    visible.value = false
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '保存就诊人失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (open) resetForm()
}, { immediate: true })

watch(() => props.patient, () => {
  if (props.modelValue) resetForm()
})
</script>

<template>
  <FormDrawer
    v-model="visible"
    class="patient-drawer"
    :title="editing ? '编辑就诊人' : '新增就诊人'"
    size="min(440px, 100vw)"
  >
    <form class="patient-form" novalidate @submit.prevent="submit">
      <p class="form-intro">请填写真实信息，挂号时可快速选择。</p>

      <div class="form-field">
        <label for="patient-name">姓名 <span aria-hidden="true">*</span></label>
        <input
          id="patient-name"
          v-model="form.name"
          name="name"
          type="text"
          autocomplete="name"
          maxlength="32"
          :disabled="submitting"
          :aria-invalid="Boolean(errors.name)"
          aria-describedby="patient-name-error"
          placeholder="请输入就诊人姓名"
        >
        <p :id="`patient-name-error`" class="field-error" role="alert">{{ errors.name }}</p>
      </div>

      <div class="form-field">
        <label for="patient-relation">与本人关系 <span aria-hidden="true">*</span></label>
        <select
          id="patient-relation"
          v-model.number="form.relation"
          name="relation"
          :disabled="submitting"
          :aria-invalid="Boolean(errors.relation)"
          aria-describedby="patient-relation-error"
        >
          <option value="">请选择关系</option>
          <option v-for="option in relationOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <p :id="`patient-relation-error`" class="field-error" role="alert">{{ errors.relation }}</p>
      </div>

      <div class="form-field">
        <label for="patient-phone">手机号 <span class="optional-label">选填</span></label>
        <input
          id="patient-phone"
          v-model="form.phone"
          name="phone"
          type="tel"
          inputmode="numeric"
          autocomplete="tel"
          maxlength="11"
          :disabled="submitting"
          :aria-invalid="Boolean(errors.phone)"
          aria-describedby="patient-phone-error"
          placeholder="请输入11位手机号"
        >
        <p :id="`patient-phone-error`" class="field-error" role="alert">{{ errors.phone }}</p>
      </div>

      <div class="form-field">
        <label for="patient-id-card">身份证号 <span class="optional-label">选填</span></label>
        <input
          id="patient-id-card"
          v-model="form.idCard"
          name="idCard"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          maxlength="18"
          :disabled="submitting"
          :aria-invalid="Boolean(errors.idCard)"
          aria-describedby="patient-id-card-error"
          placeholder="请输入18位身份证号"
        >
        <p :id="`patient-id-card-error`" class="field-error" role="alert">{{ errors.idCard }}</p>
      </div>

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

      <div class="form-actions">
        <button type="button" class="secondary-button" :disabled="submitting" @click="visible = false">取消</button>
        <button type="submit" class="primary-button" :disabled="submitting">
          {{ submitting ? '保存中…' : '保存就诊人' }}
        </button>
      </div>
    </form>
  </FormDrawer>
</template>

<style scoped>
.patient-form { display: grid; gap: 16px; padding: 4px 0 24px; }
.form-intro { margin: 0; color: var(--color-muted); font-size: 13px; line-height: 1.7; }
.form-field { display: grid; gap: 7px; }
.form-field label { color: var(--color-text); font-size: 14px; font-weight: 600; }
.form-field label > span:not(.optional-label) { color: #b42318; }
.optional-label { margin-left: 4px; color: var(--color-muted); font-size: 12px; font-weight: 400; }
.form-field input,
.form-field select {
  width: 100%;
  min-height: 46px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: white;
  color: var(--color-text);
  font-size: 15px;
}
.form-field input::placeholder { color: #91a4ad; }
.form-field input[aria-invalid="true"],
.form-field select[aria-invalid="true"] { border-color: #c2413b; }
.form-field input:disabled,
.form-field select:disabled { background: #f5f8f9; color: var(--color-muted); }
.field-error { min-height: 18px; margin: 0; color: #b42318; font-size: 12px; line-height: 1.5; }
.form-error { margin: 0; padding: 11px 12px; border: 1px solid #f1b8b3; border-radius: 10px; background: #fff2f0; color: #9f2822; font-size: 13px; line-height: 1.6; }
.form-actions { display: grid; grid-template-columns: 1fr 1.5fr; gap: 10px; margin-top: 4px; }
.primary-button,
.secondary-button { min-height: 46px; padding: 10px 16px; border-radius: 10px; font-size: 15px; font-weight: 700; cursor: pointer; }
.primary-button { border: 1px solid var(--color-brand); background: var(--color-brand); color: white; }
.secondary-button { border: 1px solid var(--color-border); background: white; color: var(--color-text); }
.primary-button:disabled,
.secondary-button:disabled { cursor: not-allowed; opacity: .6; }
@media (max-width: 560px) {
  .patient-form { padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
  .form-actions { grid-template-columns: 1fr; }
  .form-actions .primary-button { grid-row: 1; }
}
</style>
