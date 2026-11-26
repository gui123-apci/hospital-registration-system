<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { triageApi } from '@/api/triage'
import PageHeader from '@/components/PageHeader.vue'
import { timeSlotLabel } from '@/utils/schedule'
import type { TriageResult } from '@/types/models'

const MAX_SYMPTOMS = 500
const symptoms = ref('')
const submitting = ref(false)
const submitError = ref('')
const result = ref<TriageResult | null>(null)
const characterCount = computed(() => symptoms.value.length)
const canSubmit = computed(() => Boolean(symptoms.value.trim())
  && characterCount.value <= MAX_SYMPTOMS
  && !submitting.value)

watch(symptoms, () => {
  result.value = null
  submitError.value = ''
})

async function submit() {
  if (submitting.value) return
  submitError.value = ''
  result.value = null

  if (!symptoms.value.trim()) {
    submitError.value = '请描述您的症状'
    return
  }
  if (characterCount.value > MAX_SYMPTOMS) {
    submitError.value = '症状描述不能超过500字'
    return
  }

  submitting.value = true
  try {
    result.value = await triageApi.ask(symptoms.value.trim())
  } catch (reason) {
    submitError.value = reason instanceof Error ? reason.message : '导诊服务暂时不可用，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="triage-page">
    <PageHeader title="AI 导诊" subtitle="描述当前症状，获取科室就诊建议" />

    <form class="triage-form" :aria-busy="submitting" @submit.prevent="submit">
      <label for="triage-symptoms">症状描述</label>
      <p id="symptom-hint" class="field-hint">请说明主要不适、开始时间及可能的诱因。</p>
      <textarea
        id="triage-symptoms"
        v-model="symptoms"
        name="symptoms"
        rows="7"
        maxlength="500"
        :disabled="submitting"
        :aria-describedby="submitError ? 'symptom-hint character-count symptom-error' : 'symptom-hint character-count'"
        :aria-invalid="submitError ? 'true' : undefined"
        placeholder="例如：咳嗽、发热两天，体温约38℃，伴少量白痰"
      />
      <div
        id="character-count"
        class="character-count"
        :class="{ 'is-over-limit': characterCount > MAX_SYMPTOMS }"
        aria-live="polite"
      >
        {{ characterCount }} / {{ MAX_SYMPTOMS }}
      </div>

      <p v-if="submitError" id="symptom-error" class="triage-error" role="alert">{{ submitError }}</p>

      <button type="submit" class="submit-button" :disabled="!canSubmit">
        {{ submitting ? '分析中…' : '获取就诊建议' }}
      </button>
      <p class="privacy-note">请勿填写身份证号、手机号等个人敏感信息。</p>
    </form>

    <section v-if="result" class="triage-result" role="region" aria-label="导诊结果" aria-live="polite">
      <div class="recommendation-card">
        <p class="result-label">导诊建议</p>
        <p class="triage-answer">{{ result.answer }}</p>
      </div>

      <div v-if="result.schedules?.length" class="schedules-card">
        <p class="result-label">可预约号源</p>
        <ul class="schedule-list">
          <li v-for="s in result.schedules" :key="s.scheduleId" class="schedule-item">
            <div class="schedule-info">
              <span class="schedule-doctor">{{ s.doctorName }}</span>
              <span class="schedule-meta">{{ s.workDate }} {{ timeSlotLabel(s.timeSlot) }} · 余 {{ s.leftNum }} 号 · ¥{{ s.price.toFixed(2) }}</span>
            </div>
            <RouterLink
              class="book-link"
              :to="{ path: '/patient/booking', query: { scheduleId: s.scheduleId, doctorId: s.doctorId, departmentId: s.departmentId } }"
            >去挂号</RouterLink>
          </li>
        </ul>
      </div>
    </section>

    <p class="medical-disclaimer">AI 导诊仅供参考，不能替代医生诊断。如症状加重，请及时就医。</p>
  </div>
</template>

<style scoped>
.triage-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.triage-page :deep(.page-header) { margin-bottom: 20px; }
.triage-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.triage-form { display: grid; padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; box-shadow: 0 8px 24px rgba(18, 57, 74, .04); }
.triage-form > label { color: var(--color-brand-deep); font-size: 16px; font-weight: 700; }
.field-hint { margin: 7px 0 12px; color: var(--color-muted); font-size: 13px; line-height: 1.6; }
.triage-form textarea { width: 100%; min-height: 168px; resize: vertical; padding: 13px 14px; border: 1px solid var(--color-border); border-radius: 10px; background: #fbfdfe; color: var(--color-text); font-size: 15px; line-height: 1.8; }
.triage-form textarea::placeholder { color: #91a4ad; }
.triage-form textarea:disabled { background: #f5f8f9; color: var(--color-muted); }
.character-count { justify-self: end; margin-top: 7px; color: var(--color-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.character-count.is-over-limit { color: #b42318; font-weight: 700; }
.triage-error { margin: 12px 0 0; padding: 11px 12px; border: 1px solid #f1b8b3; border-radius: 10px; background: #fff2f0; color: #9f2822; font-size: 13px; line-height: 1.6; }
.submit-button { min-height: 48px; margin-top: 16px; padding: 11px 18px; border: 1px solid var(--color-brand); border-radius: 11px; background: var(--color-brand); color: white; font-size: 16px; font-weight: 700; cursor: pointer; }
.submit-button:disabled { cursor: not-allowed; opacity: .6; }
.privacy-note { margin: 10px 0 0; color: var(--color-muted); font-size: 12px; line-height: 1.6; text-align: center; }
.triage-result { display: grid; gap: 14px; margin-top: 18px; }
.recommendation-card { padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.result-label { margin: 0 0 8px; color: var(--color-brand); font-size: 13px; font-weight: 700; }
.triage-answer { margin: 0; color: var(--color-text); font-size: 14px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.schedules-card { padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.schedule-list { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
.schedule-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 14px; border: 1px solid var(--color-border); border-radius: 12px; background: #fbfdfe; }
.schedule-info { display: grid; gap: 4px; min-width: 0; }
.schedule-doctor { color: var(--color-text); font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.schedule-meta { color: var(--color-muted); font-size: 12px; line-height: 1.6; }
.book-link { flex: none; padding: 9px 16px; border-radius: 10px; background: var(--color-brand); color: white; font-size: 13px; font-weight: 700; text-decoration: none; white-space: nowrap; }
.medical-disclaimer { margin: 18px 0 0; color: var(--color-muted); font-size: 12px; line-height: 1.7; text-align: center; }
@media (max-width: 560px) {
  .triage-page { padding: 20px 14px 28px; }
  .triage-form { padding: 17px 15px; }
  .triage-form textarea { min-height: 156px; }
  .recommendation-card { padding: 17px 15px; }
}
</style>
