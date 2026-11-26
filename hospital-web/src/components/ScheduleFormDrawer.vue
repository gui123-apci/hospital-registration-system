<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import { adminDoctorApi } from '@/api/adminDoctor'
import { adminScheduleApi, type ScheduleCreateRequest, type ScheduleUpdateRequest } from '@/api/adminSchedule'
import FormDrawer from '@/components/FormDrawer.vue'
import NameIdSelect, { type NameIdOption } from '@/components/NameIdSelect.vue'
import type { Schedule, TimeSlot } from '@/types/models'
import { isScheduleExpired, localDateString, timeSlotLabel } from '@/utils/schedule'

const props = defineProps<{ modelValue: boolean; schedule?: Schedule | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [schedule: Schedule] }>()

const form = reactive<{
  doctorId?: number
  workDate: string
  timeSlot?: TimeSlot
  totalNum: number | string
  price: number | string
  status: 0 | 1 | undefined
}>({ doctorId: undefined, workDate: '', timeSlot: undefined, totalNum: '', price: '', status: 1 })
const errors = reactive({ doctorId: '', workDate: '', timeSlot: '', totalNum: '', price: '', status: '' })
const options = ref<NameIdOption[]>([])
const optionsLoading = ref(false)
const optionsError = ref('')
const submitError = ref('')
const submitting = ref(false)
const saved = ref(false)
const closedWhileSubmitting = ref(false)
let optionsRequest = 0
const editing = computed(() => Boolean(props.schedule))
const today = computed(() => localDateString())
const minimumDate = computed(() => props.schedule?.workDate && props.schedule.workDate < today.value ? props.schedule.workDate : today.value)
const locked = computed(() => submitting.value || saved.value)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    if (value || !submitting.value) emit('update:modelValue', value)
  }
})
const slots: TimeSlot[] = [1, 2, 3]

function resetForm() {
  form.doctorId = props.schedule?.doctorId
  form.workDate = props.schedule?.workDate ?? ''
  form.timeSlot = props.schedule?.timeSlot
  form.totalNum = props.schedule?.totalNum ?? ''
  form.price = props.schedule?.price ?? ''
  form.status = props.schedule?.status ?? 1
  errors.doctorId = ''
  errors.workDate = ''
  errors.timeSlot = ''
  errors.totalNum = ''
  errors.price = ''
  errors.status = ''
  submitError.value = ''
}

async function loadOptions() {
  const request = ++optionsRequest
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const result = await adminDoctorApi.list()
    if (request !== optionsRequest) return
    options.value = result.map((doctor) => ({ label: doctor.name, value: doctor.id }))
  } catch (reason) {
    if (request !== optionsRequest) return
    options.value = []
    optionsError.value = reason instanceof Error ? reason.message : '医生选项加载失败，请稍后重试'
  } finally {
    if (request === optionsRequest) optionsLoading.value = false
  }
}

function validate() {
  const original = props.schedule
  const totalNum = Number(form.totalNum)
  const price = Number(form.price)
  const priceText = String(form.price)
  const unchangedDate = Boolean(original && form.workDate === original.workDate)
  const unchangedSlot = Boolean(original && form.timeSlot === original.timeSlot)
  const changedDate = !original || form.workDate !== original.workDate
  const changedSlot = !original || form.timeSlot !== original.timeSlot
  const expired = Boolean(form.workDate && form.timeSlot && isScheduleExpired(form.workDate, form.timeSlot))

  errors.doctorId = !editing.value && form.doctorId === undefined ? '请选择医生' : ''
  errors.workDate = !form.workDate ? '请选择出诊日期'
    : changedDate && form.workDate < today.value ? '出诊日期不能早于今天' : ''
  errors.timeSlot = form.timeSlot === undefined ? '请选择出诊时段'
    : !original && expired ? '该时段已开始，不能新增排班'
      : changedSlot && expired ? '不能将排班调整到已开始的时段' : ''
  errors.totalNum = form.totalNum === '' ? '请输入放号数量'
    : !Number.isInteger(totalNum) || totalNum < 1 ? '放号数量必须为大于等于 1 的整数' : ''
  errors.price = form.price === '' || !Number.isFinite(price) ? '请输入挂号费'
    : price < 0 || price > 99999999.99 ? '挂号费必须在 0 到 99999999.99 之间'
      : !/^\d+(\.\d{1,2})?$/.test(priceText) ? '挂号费最多保留两位小数' : ''
  if (original && form.totalNum !== '' && Number.isInteger(totalNum) && totalNum >= 1) {
    const booked = original.totalNum - original.leftNum
    if (totalNum < booked) errors.totalNum = `放号数量不能少于已挂号 ${booked} 个`
  }
  errors.status = form.status === undefined ? '请选择排班状态'
    : !editing.value && expired ? '该时段已开始，不能新增排班'
      : original && !unchangedDate && !unchangedSlot && expired ? '不能将排班调整到已开始的时段'
        : form.status === 1 && expired ? '已过期排班不能改为可预约' : ''
  return !Object.values(errors).some(Boolean)
}

async function submit() {
  if (submitting.value || saved.value || !props.modelValue || optionsLoading.value || optionsError.value) return
  submitError.value = ''
  if (!validate() || form.timeSlot === undefined || form.status === undefined) return

  submitting.value = true
  closedWhileSubmitting.value = false
  try {
    const common = {
      timeSlot: form.timeSlot,
      totalNum: Number(form.totalNum),
      price: Number(form.price),
      status: form.status
    }
    let schedule: Schedule
    if (props.schedule) {
      const payload: ScheduleUpdateRequest = {
        id: props.schedule.id,
        ...common,
        ...(form.workDate !== props.schedule.workDate ? { workDate: form.workDate } : {})
      }
      schedule = await adminScheduleApi.update(payload)
    } else {
      if (form.doctorId === undefined) return
      const payload: ScheduleCreateRequest = { doctorId: form.doctorId, workDate: form.workDate, ...common }
      schedule = await adminScheduleApi.create(payload)
    }
    saved.value = true
    emit('update:modelValue', false)
    emit('saved', schedule)
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '排班保存失败，请稍后重试'
  } finally {
    submitting.value = false
    if (closedWhileSubmitting.value || !props.modelValue) {
      saved.value = false
      closedWhileSubmitting.value = false
      resetForm()
    }
  }
}

watch(() => props.modelValue, (open) => {
  if (open) {
    if (!submitting.value && !saved.value) {
      resetForm()
      void loadOptions()
    }
  } else if (submitting.value) {
    closedWhileSubmitting.value = true
  } else {
    saved.value = false
    optionsRequest += 1
    options.value = []
  }
}, { immediate: true })

watch(() => props.schedule, () => {
  if (props.modelValue && !submitting.value && !saved.value) resetForm()
})
</script>

<template>
  <FormDrawer v-model="visible" :title="editing ? '编辑排班' : '新增排班'" size="min(560px, 100vw)">
    <form class="schedule-form" novalidate @submit.prevent="submit">
      <p class="form-intro">设置医生出诊日期、时段与号源。更换医生请删除原排班后重新新增。</p>
      <ElAlert v-if="submitError" class="schedule-error" type="error" :title="submitError" role="alert" show-icon :closable="false" />

      <div class="form-field">
        <label for="schedule-doctor">出诊医生 <span aria-hidden="true">*</span></label>
        <NameIdSelect
          id="schedule-doctor"
          v-model="form.doctorId"
          name="doctorId"
          class="doctor-select"
          :class="{ 'is-invalid': Boolean(errors.doctorId) }"
          :options="options"
          placeholder="请选择医生"
          :disabled="locked || editing || optionsLoading || Boolean(optionsError)"
          aria-label="请选择医生"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.doctorId)"
          :aria-describedby="errors.doctorId ? 'schedule-doctorId-error' : optionsError ? 'schedule-options-error' : undefined"
          @update:model-value="errors.doctorId = ''; submitError = ''"
        />
        <p v-if="optionsLoading" class="options-status" role="status">正在加载医生…</p>
        <div v-if="optionsError" id="schedule-options-error" class="options-error" role="alert">
          <span>{{ optionsError }}</span>
          <ElButton :loading="optionsLoading" @click="loadOptions">重试</ElButton>
        </div>
        <p v-if="errors.doctorId" id="schedule-doctorId-error" class="field-error" role="alert">{{ errors.doctorId }}</p>
        <p v-if="editing" class="field-hint">医生不可修改，请删除后重新创建以更换医生。</p>
      </div>

      <div class="form-grid">
        <div class="form-field">
          <label for="schedule-workDate">出诊日期 <span aria-hidden="true">*</span></label>
          <ElInput
            id="schedule-workDate"
            v-model="form.workDate"
            name="workDate"
            type="date"
            aria-label="出诊日期"
            aria-required="true"
            required
            :min="minimumDate"
            :disabled="locked"
            :aria-invalid="Boolean(errors.workDate)"
            :aria-describedby="errors.workDate ? 'schedule-workDate-error' : undefined"
            @input="errors.workDate = ''; submitError = ''"
          />
          <p v-if="errors.workDate" id="schedule-workDate-error" class="field-error" role="alert">{{ errors.workDate }}</p>
        </div>
        <div class="form-field">
          <label for="schedule-timeSlot">出诊时段 <span aria-hidden="true">*</span></label>
          <ElSelect
            id="schedule-timeSlot"
            v-model="form.timeSlot"
            name="timeSlot"
            placeholder="出诊时段"
            :disabled="locked"
            aria-label="出诊时段"
            aria-required="true"
            required
            :aria-invalid="Boolean(errors.timeSlot)"
            :aria-describedby="errors.timeSlot ? 'schedule-timeSlot-error' : undefined"
            @update:model-value="errors.timeSlot = ''; errors.status = ''; submitError = ''"
          >
            <ElOption v-for="slot in slots" :key="slot" :label="timeSlotLabel(slot)" :value="slot" />
          </ElSelect>
          <p v-if="errors.timeSlot" id="schedule-timeSlot-error" class="field-error" role="alert">{{ errors.timeSlot }}</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="form-field">
          <label for="schedule-totalNum">放号数量 <span aria-hidden="true">*</span></label>
          <ElInput
            id="schedule-totalNum"
            v-model.number="form.totalNum"
            name="totalNum"
            type="number"
            min="1"
            step="1"
            aria-label="放号数量"
            aria-required="true"
            required
            :disabled="locked"
            :aria-invalid="Boolean(errors.totalNum)"
            :aria-describedby="errors.totalNum ? 'schedule-totalNum-error' : undefined"
            @input="errors.totalNum = ''; submitError = ''"
          />
          <p v-if="errors.totalNum" id="schedule-totalNum-error" class="field-error" role="alert">{{ errors.totalNum }}</p>
        </div>
        <div class="form-field">
          <label for="schedule-price">挂号费 <span aria-hidden="true">*</span></label>
          <ElInput
            id="schedule-price"
            v-model.number="form.price"
            name="price"
            type="number"
            min="0"
            max="99999999.99"
            step="0.01"
            aria-label="挂号费"
            aria-required="true"
            required
            :disabled="locked"
            :aria-invalid="Boolean(errors.price)"
            :aria-describedby="errors.price ? 'schedule-price-error' : undefined"
            @input="errors.price = ''; submitError = ''"
          />
          <p v-if="errors.price" id="schedule-price-error" class="field-error" role="alert">{{ errors.price }}</p>
        </div>
      </div>

      <div class="form-field">
        <label for="schedule-status">排班状态 <span aria-hidden="true">*</span></label>
        <ElSelect
          id="schedule-status"
          v-model="form.status"
          name="status"
          placeholder="排班状态"
          :disabled="locked"
          aria-label="排班状态"
          aria-required="true"
          required
          :aria-invalid="Boolean(errors.status)"
          :aria-describedby="errors.status ? 'schedule-status-error' : undefined"
          @update:model-value="errors.status = ''; submitError = ''"
        >
          <ElOption label="可预约" :value="1" />
          <ElOption label="停诊" :value="0" />
        </ElSelect>
        <p v-if="errors.status" id="schedule-status-error" class="field-error" role="alert">{{ errors.status }}</p>
      </div>

      <div class="form-actions">
        <ElButton :disabled="locked" @click="visible = false">取消</ElButton>
        <ElButton type="primary" native-type="submit" :loading="submitting" :disabled="locked || optionsLoading || Boolean(optionsError)">
          {{ submitting ? '正在保存…' : editing ? '保存修改' : '新增排班' }}
        </ElButton>
      </div>
    </form>
  </FormDrawer>
</template>

<style scoped>
.schedule-form { display: grid; gap: 18px; padding: 4px 0 24px; }
.form-intro, .field-hint { margin: 0; color: var(--color-muted); font-size: 13px; line-height: 1.7; }
.schedule-error { margin: 0; }
.form-field { display: grid; gap: 8px; min-width: 0; }
.form-field label { color: var(--color-text); font-size: 14px; font-weight: 700; }
.form-field label > span:not(.field-hint) { color: #b42318; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.doctor-select { width: 100%; }
.field-error, .options-status { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.5; }
.field-error { color: #b42318; }
.options-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border: 1px solid #f0c9c4; border-radius: 8px; background: #fff5f4; color: #b42318; font-size: 13px; }
.options-error :deep(.el-button) { min-height: 36px; margin-left: 0; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.form-actions :deep(.el-button) { min-height: 44px; margin-left: 0; border-radius: 10px; font-weight: 700; }
.schedule-form :deep(.el-input__wrapper:focus-within),
.schedule-form :deep(.el-select__wrapper:focus-within) { box-shadow: 0 0 0 3px rgba(8, 127, 122, .16); }
.schedule-form :deep(.el-input.is-invalid .el-input__wrapper),
.doctor-select.is-invalid :deep(.el-select__wrapper) { box-shadow: 0 0 0 1px #b42318; }
@media (max-width: 520px) {
  .schedule-form { padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
  .form-grid { grid-template-columns: 1fr; gap: 18px; }
  .form-actions { display: grid; grid-template-columns: 1fr 1.5fr; }
}
</style>
