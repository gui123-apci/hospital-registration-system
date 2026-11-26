<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput } from 'element-plus'
import FormDrawer from '@/components/FormDrawer.vue'
import { doctorWorkApi } from '@/api/doctorWork'

const props = defineProps<{ modelValue: boolean; orderId: number; patientName: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const reportType = ref('')
const title = ref('')
const content = ref('')
const submitting = ref(false)
const saved = ref(false)
const attempted = ref(false)
const submitError = ref('')
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => {
    if (value || !submitting.value) emit('update:modelValue', value)
  }
})

const typeError = computed(() => reportType.value.length > 32 ? '报告类型不能超过 32 个字' : '')
const titleValue = computed(() => title.value.trim())
const titleError = computed(() => {
  if (!titleValue.value) return '请输入报告标题'
  return titleValue.value.length > 64 ? '报告标题不能超过 64 个字' : ''
})
const invalid = computed(() => Boolean(typeError.value || titleError.value))

watch(() => props.modelValue, (visible) => {
  if (!visible || submitting.value || saved.value) return
  reportType.value = ''
  title.value = ''
  content.value = ''
  attempted.value = false
  submitError.value = ''
})

function handleBeforeClose(done: () => void) {
  if (!submitting.value) done()
}

async function submit() {
  if (submitting.value || saved.value) return
  attempted.value = true
  submitError.value = ''
  if (invalid.value) return

  submitting.value = true
  try {
    await doctorWorkApi.report({
      orderId: props.orderId,
      reportType: reportType.value.trim(),
      title: titleValue.value,
      content: content.value.trim()
    })
    saved.value = true
    emit('update:modelValue', false)
    emit('saved')
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '报告保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <FormDrawer
    v-model="visible"
    title="填写就诊报告"
    size="min(520px, 100vw)"
    :before-close="handleBeforeClose"
  >
    <form class="report-form" novalidate @submit.prevent="submit">
      <div class="report-patient">
        <span>当前就诊人</span>
        <strong>{{ patientName }}</strong>
      </div>

      <ElAlert
        v-if="submitError"
        class="report-error"
        type="error"
        :title="submitError"
        show-icon
        :closable="false"
      />

      <div class="form-field">
        <label for="report-type">报告类型 <span class="optional">选填</span></label>
        <ElInput
          id="report-type"
          v-model="reportType"
          aria-label="报告类型"
          :aria-invalid="attempted && Boolean(typeError)"
          :aria-describedby="attempted && typeError ? 'report-type-error' : undefined"
          :maxlength="32"
          :disabled="submitting"
          show-word-limit
          placeholder="如：门诊报告"
          @input="submitError = ''"
        />
        <p v-if="attempted && typeError" id="report-type-error" class="field-error" role="alert">{{ typeError }}</p>
      </div>

      <div class="form-field">
        <label for="report-title">报告标题 <span class="required">*</span></label>
        <ElInput
          id="report-title"
          v-model="title"
          aria-label="报告标题"
          :aria-invalid="attempted && Boolean(titleError)"
          :aria-describedby="attempted && titleError ? 'report-title-error' : undefined"
          :maxlength="64"
          :disabled="submitting"
          show-word-limit
          placeholder="请输入报告标题"
          @input="submitError = ''"
        />
        <p v-if="attempted && titleError" id="report-title-error" class="field-error" role="alert">{{ titleError }}</p>
      </div>

      <div class="form-field">
        <label for="report-content">报告内容 <span class="optional">选填</span></label>
        <ElInput
          id="report-content"
          v-model="content"
          aria-label="报告内容"
          type="textarea"
          :rows="7"
          :disabled="submitting"
          placeholder="记录诊断结论、医嘱或复诊建议"
          @input="submitError = ''"
        />
      </div>

      <p class="report-hint">保存成功后，当前患者将完成就诊，可继续叫下一位。</p>
      <div class="report-actions">
        <ElButton :disabled="submitting" @click="emit('update:modelValue', false)">取消</ElButton>
        <ElButton type="primary" native-type="submit" :loading="submitting" :disabled="submitting || saved">
          {{ submitting ? '正在保存…' : '保存报告' }}
        </ElButton>
      </div>
    </form>
  </FormDrawer>
</template>

<style scoped>
.report-form { display: grid; gap: 20px; }
.report-patient { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-radius: 11px; background: var(--color-brand-soft); }
.report-patient span { color: var(--color-muted); font-size: 13px; }
.report-patient strong { color: var(--color-brand-deep); }
.form-field { display: grid; gap: 8px; }
.form-field label { color: var(--color-text); font-size: 14px; font-weight: 700; }
.optional { margin-left: 4px; color: var(--color-muted); font-size: 12px; font-weight: 400; }
.required { color: #b04b3c; }
.field-error { margin: 0; color: #b04b3c; font-size: 12px; line-height: 1.5; }
.report-hint { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.7; }
.report-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
.report-actions :deep(.el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
.report-form :deep(.el-input__wrapper:focus-within),
.report-form :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 3px rgba(8, 127, 122, .16); }
.report-form :deep(.el-input.is-invalid .el-input__wrapper),
.report-form :deep(.el-textarea.is-invalid .el-textarea__inner) { box-shadow: 0 0 0 1px #b04b3c; }
@media (max-width: 520px) {
  .report-actions { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>
