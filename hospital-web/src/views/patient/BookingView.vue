<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { doctorApi } from '@/api/doctor'
import { orderApi } from '@/api/order'
import { patientApi } from '@/api/patient'
import { scheduleApi } from '@/api/schedule'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'
import { relationLabel } from '@/utils/patient'
import { scheduleLabel } from '@/utils/schedule'

const route = useRoute()
const router = useRouter()
const selectedPatientId = ref<number>()
const submitting = ref(false)
const submitError = ref('')
const successMessage = ref('')
const createdOrderId = ref<number>()
/** 排班/医生上下文版本：提交途中切换了链接，旧请求的结果就不再写回页面 */
let contextVersion = 0

function readPositiveId(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

const { data: context, loading, error, reload } = useAsync(async () => {
  const scheduleId = readPositiveId(route.query.scheduleId)
  const doctorId = readPositiveId(route.query.doctorId)
  const departmentId = readPositiveId(route.query.departmentId)
  if (scheduleId === null || doctorId === null || departmentId === null) {
    throw new Error('挂号链接信息不完整或无效，请返回重新选择排班')
  }

  const [doctors, schedules, patients] = await Promise.all([
    doctorApi.list(departmentId),
    scheduleApi.list(doctorId),
    patientApi.list()
  ])
  const doctor = doctors.find((item) => item.id === doctorId && item.departmentId === departmentId)
  const schedule = schedules.find((item) =>
    item.id === scheduleId && item.doctorId === doctorId && item.departmentId === departmentId
  )
  if (!doctor || !schedule) throw new Error('未找到对应的医生或排班，请返回重新选择')
  if (schedule.status !== 1 || schedule.leftNum <= 0) {
    throw new Error('该排班当前不可预约或已约满，请重新选择排班')
  }

  return { doctor, schedule, patients }
})

const doctor = computed(() => context.value?.doctor ?? null)
const schedule = computed(() => context.value?.schedule ?? null)
const patients = computed(() => context.value?.patients ?? [])
const patientOptions = computed(() => patients.value.map((patient) => ({
  label: `${patient.name} · 关系：${relationLabel(patient.relation)}`,
  value: patient.id
})))
const hasSelectedPatient = computed(() => selectedPatientId.value !== undefined
  && patients.value.some((patient) => patient.id === selectedPatientId.value))
const canSubmit = computed(() => !loading.value
  && !submitting.value
  && createdOrderId.value === undefined
  && Boolean(schedule.value)
  && hasSelectedPatient.value)
const backToSchedules = computed(() => {
  const doctorId = readPositiveId(route.query.doctorId)
  const departmentId = readPositiveId(route.query.departmentId)
  return {
    path: doctorId === null ? '/patient/departments' : '/patient/schedules',
    query: doctorId === null ? {} : { doctorId, departmentId: departmentId ?? undefined }
  }
})

async function submit() {
  if (!canSubmit.value || !schedule.value || selectedPatientId.value === undefined) return

  submitting.value = true
  submitError.value = ''
  const version = contextVersion
  try {
    const result = await orderApi.create({
      scheduleId: schedule.value.id,
      patientId: selectedPatientId.value
    })
    // 提交期间用户已经换了排班：这次结果属于旧上下文，不能跳去旧订单的支付页
    if (version !== contextVersion) return
    createdOrderId.value = result.order.id
    successMessage.value = result.message?.trim() || '挂号成功，请完成支付'
    ElMessage.success(successMessage.value)
    await router.push({ path: '/patient/payment', query: { orderId: result.order.id } })
  } catch (reason) {
    if (version !== contextVersion) return
    if (createdOrderId.value === undefined) {
      submitError.value = reason instanceof Error ? reason.message : '挂号失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

watch(
  [() => route.query.scheduleId, () => route.query.doctorId, () => route.query.departmentId],
  () => {
    contextVersion += 1
    context.value = null
    selectedPatientId.value = undefined
    submitError.value = ''
    successMessage.value = ''
    createdOrderId.value = undefined
    void reload()
  }
)

onMounted(reload)
</script>

<template>
  <div class="booking-page">
    <PageHeader title="确认挂号" subtitle="请核对就诊信息并选择就诊人">
      <RouterLink :to="backToSchedules" class="back-link">重新选排班</RouterLink>
    </PageHeader>

    <div v-if="loading" class="loading-card" role="status" aria-label="正在加载挂号信息">
      <ElSkeleton :rows="5" animated />
    </div>
    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <form v-else-if="context" class="booking-form" @submit.prevent="submit">
      <section class="info-card" aria-labelledby="visit-info-title">
        <div class="card-heading">
          <h2 id="visit-info-title">就诊信息</h2>
          <span class="confirmed-label">已选择</span>
        </div>
        <dl class="visit-details">
          <div><dt>就诊医生</dt><dd>{{ doctor?.name }}<span v-if="doctor?.title" class="detail-note">{{ doctor.title }}</span></dd></div>
          <div><dt>就诊日期</dt><dd>{{ schedule?.workDate }}</dd></div>
          <div><dt>出诊时段</dt><dd>{{ schedule ? scheduleLabel(schedule, doctor?.name ?? '医生') : '' }}</dd></div>
          <div><dt>挂号费用</dt><dd class="amount">¥{{ schedule?.price.toFixed(2) }}</dd></div>
        </dl>
      </section>

      <section class="info-card" aria-labelledby="patient-title">
        <div class="card-heading">
          <h2 id="patient-title">选择就诊人</h2>
        </div>
        <EmptyState v-if="!patients.length" description="暂无就诊人，请添加就诊人后再挂号" />
        <NameIdSelect
          v-else
          v-model="selectedPatientId"
          :options="patientOptions"
          :disabled="submitting || createdOrderId !== undefined"
          placeholder="请选择就诊人"
          aria-label="选择就诊人"
        />
      </section>

      <ElAlert v-if="submitError" class="form-alert" type="error" :title="submitError" show-icon :closable="false" />
      <ElAlert v-if="successMessage" class="form-alert" type="success" :title="successMessage" show-icon :closable="false" />

      <div class="submit-area">
        <p>提交后将为您锁定本次挂号，请按提示完成支付。</p>
        <ElButton
          v-if="createdOrderId === undefined"
          type="primary"
          size="large"
          class="submit-button"
          :loading="submitting"
          :disabled="!canSubmit"
          native-type="submit"
        >
          {{ submitting ? '正在提交…' : '确认挂号' }}
        </ElButton>
        <RouterLink v-else :to="{ path: '/patient/payment', query: { orderId: createdOrderId } }" class="payment-link">
          前往支付
        </RouterLink>
      </div>
    </form>
  </div>
</template>

<style scoped>
.booking-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.booking-page :deep(.page-header) { margin-bottom: 20px; }
.booking-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.loading-card { padding: 24px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.booking-form { display: grid; gap: 14px; }
.info-card { padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.card-heading h2 { margin: 0; color: var(--color-brand-deep); font-size: 17px; }
.confirmed-label { padding: 3px 8px; border-radius: 6px; background: var(--color-brand-soft); color: var(--color-brand); font-size: 12px; }
.visit-details { display: grid; gap: 0; margin: 0; }
.visit-details > div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 12px; padding: 13px 0; border-top: 1px solid #edf2f4; }
.visit-details dt { color: var(--color-muted); font-size: 13px; }
.visit-details dd { margin: 0; font-size: 14px; font-weight: 600; line-height: 1.6; overflow-wrap: anywhere; }
.detail-note { margin-left: 8px; color: var(--color-muted); font-size: 12px; font-weight: 400; }
.visit-details .amount { color: #b05b36; font-size: 21px; }
.info-card :deep(.el-select) { width: 100%; }
.info-card :deep(.el-empty) { padding: 12px 0 4px; }
.form-alert { margin-top: 2px; }
.submit-area { display: grid; gap: 12px; padding: 8px 2px 0; }
.submit-area p { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.7; text-align: center; }
.submit-button { width: 100%; min-height: 48px; border-radius: 11px; font-size: 16px; font-weight: 700; }
.payment-link { display: grid; min-height: 48px; place-items: center; border-radius: 11px; background: var(--color-brand); color: white; font-size: 16px; font-weight: 700; text-decoration: none; }
@media (max-width: 560px) {
  .booking-page { padding: 20px 14px 28px; }
  .booking-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .info-card { padding: 17px 15px; }
  .visit-details > div { grid-template-columns: 78px minmax(0, 1fr); }
}
</style>
