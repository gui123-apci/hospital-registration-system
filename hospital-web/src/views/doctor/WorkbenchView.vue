<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import NameIdSelect from '@/components/NameIdSelect.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import PageHeader from '@/components/PageHeader.vue'
import ReportDrawer from '@/components/ReportDrawer.vue'
import { doctorWorkApi } from '@/api/doctorWork'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import type { Order, Schedule, TimeSlot } from '@/types/models'

const consultationStorageKey = 'doctor-work:active-consultation'

const slotText: Record<TimeSlot, { label: string; range: string }> = {
  1: { label: '上午', range: '08:00—12:00' },
  2: { label: '下午', range: '13:00—17:00' },
  3: { label: '晚间', range: '18:00—21:00' }
}

function dateLabel(workDate: string) {
  const [, month, day] = workDate.split('-').map(Number)
  return `${month}月${day}日`
}

function scheduleLabel(schedule: Schedule) {
  const slot = slotText[schedule.timeSlot]
  return `${dateLabel(schedule.workDate)} · ${slot.label} · ${slot.range}`
}

interface StoredConsultation {
  userId: number
  scheduleId: number
  order: Order
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isDateString(value: unknown): value is string {
  return typeof value === 'string' && !Number.isNaN(Date.parse(value))
}

function isOrder(value: unknown): value is Order {
  if (!isRecord(value)) return false
  return isPositiveInteger(value.id)
    && isPositiveInteger(value.patientId)
    && isPositiveInteger(value.scheduleId)
    && isNonEmptyString(value.orderNo)
    && isNonEmptyString(value.patientName)
    && isDateString(value.workDate)
    && (value.timeSlot === 1 || value.timeSlot === 2 || value.timeSlot === 3)
    && typeof value.amount === 'number' && Number.isFinite(value.amount) && value.amount >= 0
    && value.status === 6
    && isNonEmptyString(value.statusDesc)
    && isDateString(value.createTime)
    && (value.deadlineTime === undefined || isDateString(value.deadlineTime))
}

function storedOrder(order: Order): Order {
  return {
    id: order.id,
    orderNo: order.orderNo,
    patientId: order.patientId,
    patientName: order.patientName,
    scheduleId: order.scheduleId,
    workDate: order.workDate,
    timeSlot: order.timeSlot,
    amount: order.amount,
    status: order.status,
    statusDesc: order.statusDesc,
    deadlineTime: order.deadlineTime,
    createTime: order.createTime
  }
}

function clearStoredConsultation() {
  try {
    sessionStorage.removeItem(consultationStorageKey)
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}

function persistConsultation(userId: number | undefined, scheduleId: number, order: Order) {
  if (!isPositiveInteger(userId)) return
  try {
    sessionStorage.setItem(consultationStorageKey, JSON.stringify({
      userId,
      scheduleId,
      order: storedOrder(order)
    } satisfies StoredConsultation))
  } catch {
    // The active in-memory consultation must still work without storage.
  }
}

/**
 * 读取本机缓存的进行中就诊。只校验形状与账号归属，
 * 排班是否仍属于当前医生要等服务端排班列表回来后再判断。
 */
function readStoredConsultation(userId: number | undefined): StoredConsultation | null {
  try {
    const raw = sessionStorage.getItem(consultationStorageKey)
    if (raw === null) return null
    const value: unknown = JSON.parse(raw)
    if (!isRecord(value)
      || userId === undefined
      || !isPositiveInteger(value.userId)
      || value.userId !== userId
      || !isPositiveInteger(value.scheduleId)
      || !isOrder(value.order)
      || value.order.scheduleId !== value.scheduleId) {
      clearStoredConsultation()
      return null
    }
    return { userId: value.userId, scheduleId: value.scheduleId, order: storedOrder(value.order) }
  } catch {
    clearStoredConsultation()
    return null
  }
}

const auth = useAuthStore()
const scheduleId = ref<number>()
const activeOrder = ref<Order | null>(null)
const reportOpen = ref(false)
const calling = ref(false)
const completing = ref(false)
const callError = ref('')
const actionSuccess = ref('')
const queueWarning = ref('')
let contextVersion = 0

const {
  data: scheduleData,
  loading: schedulesLoading,
  error: schedulesError,
  reload: reloadSchedules
} = useAsync(() => doctorWorkApi.schedules())
const schedules = computed<Schedule[]>(() => scheduleData.value ?? [])
const scheduleOptions = computed(() => schedules.value.map((schedule) => ({
  label: scheduleLabel(schedule),
  value: schedule.id
})))
const selectedSchedule = computed(() => schedules.value.find((schedule) => schedule.id === scheduleId.value))

const { data: waiting, loading, error, reload } = useAsync(async () => {
  if (scheduleId.value === undefined) return []
  return doctorWorkApi.waiting(scheduleId.value)
})

// 服务端排班回来后再恢复本机缓存的进行中就诊：排班已不属于当前医生（换了排班/被删）就丢弃
const storedConsultation = readStoredConsultation(auth.user?.id)
let consultationRestored = false
watch(scheduleData, (list) => {
  if (consultationRestored || list === null) return
  consultationRestored = true
  if (!storedConsultation) return
  if (!list.some((schedule) => schedule.id === storedConsultation.scheduleId)) {
    clearStoredConsultation()
    return
  }
  scheduleId.value = storedConsultation.scheduleId
  activeOrder.value = storedConsultation.order
  void reload()
}, { immediate: true })
void reloadSchedules()
const canCall = computed(() => scheduleId.value !== undefined
  && !loading.value
  && !error.value
  && !activeOrder.value
  && !calling.value
  && Boolean(waiting.value?.length))

function changeSchedule(value: number | undefined) {
  if (activeOrder.value || calling.value || value === scheduleId.value) return
  contextVersion += 1
  scheduleId.value = value
  activeOrder.value = null
  reportOpen.value = false
  callError.value = ''
  actionSuccess.value = ''
  queueWarning.value = ''
  if (value !== undefined) void reload()
}

async function refreshAfterMutation(successMessage: string, requestContext: number) {
  await reload()
  if (requestContext !== contextVersion || scheduleId.value === undefined) return
  if (error.value) {
    queueWarning.value = `${successMessage}，但候诊队列刷新失败：${error.value}。请重试刷新，无需重复提交。`
  }
}

async function callNext() {
  if (!canCall.value || scheduleId.value === undefined) return
  const targetScheduleId = scheduleId.value
  const requestContext = contextVersion
  calling.value = true
  callError.value = ''
  actionSuccess.value = ''
  queueWarning.value = ''
  try {
    const order = await doctorWorkApi.call(targetScheduleId)
    if (requestContext !== contextVersion) return
    activeOrder.value = order
    persistConsultation(auth.user?.id, targetScheduleId, order)
    actionSuccess.value = `已叫号：${order.patientName}`
    await refreshAfterMutation('叫号成功', requestContext)
  } catch (reason) {
    if (requestContext === contextVersion) {
      callError.value = reason instanceof Error ? reason.message : '叫号失败，请稍后重试'
    }
  } finally {
    if (requestContext === contextVersion) calling.value = false
  }
}

function retryQueue() {
  queueWarning.value = ''
  void reload()
}

async function completeConsultation() {
  if (!activeOrder.value || completing.value) return
  const targetOrder = activeOrder.value
  const requestContext = contextVersion
  completing.value = true
  callError.value = ''
  actionSuccess.value = ''
  queueWarning.value = ''
  try {
    await doctorWorkApi.complete(targetOrder.id)
    if (requestContext !== contextVersion) return
    activeOrder.value = null
    clearStoredConsultation()
    actionSuccess.value = `已完成接诊：${targetOrder.patientName}`
    if (scheduleId.value !== undefined) await refreshAfterMutation('完成接诊', requestContext)
  } catch (reason) {
    if (requestContext === contextVersion) {
      callError.value = reason instanceof Error ? reason.message : '完成接诊失败，请稍后重试'
    }
  } finally {
    if (requestContext === contextVersion) completing.value = false
  }
}

async function reportSaved() {
  // 报告保存只代表"开了一份报告"，不代表就诊结束；医生还需点「完成接诊」收尾
  reportOpen.value = false
  actionSuccess.value = '报告已保存'
  queueWarning.value = ''
}
</script>

<template>
  <div class="workbench-page">
    <PageHeader title="医生工作台" subtitle="按签到顺序叫号，填写报告后点击完成接诊" />

    <ElAlert
      v-if="actionSuccess"
      class="success-alert"
      type="success"
      :title="actionSuccess"
      show-icon
      :closable="false"
    />
    <ElAlert
      v-if="queueWarning"
      class="queue-warning"
      type="warning"
      :title="queueWarning"
      show-icon
      :closable="false"
    />

    <section class="schedule-panel" aria-labelledby="schedule-heading">
      <div class="section-heading">
        <div>
          <span class="section-kicker">WORK SESSION</span>
          <h2 id="schedule-heading">选择出诊排班</h2>
        </div>
        <span v-if="selectedSchedule" class="session-date">{{ selectedSchedule.workDate }} · {{ slotText[selectedSchedule.timeSlot].label }}</span>
      </div>
      <div class="schedule-field">
        <span id="schedule-select-label">当前排班</span>
        <NameIdSelect
          id="doctor-schedule"
          :model-value="scheduleId"
          :options="scheduleOptions"
          :disabled="Boolean(activeOrder) || calling || schedulesLoading"
          placeholder="请选择排班"
          aria-label="选择排班"
          @update:model-value="changeSchedule"
        />
      </div>
      <p v-if="schedulesError" class="schedule-notice">
        排班加载失败：{{ schedulesError }}
        <ElButton text :disabled="schedulesLoading" @click="reloadSchedules">重新加载</ElButton>
      </p>
      <p v-else-if="!schedulesLoading && !schedules.length" class="schedule-notice">
        当前没有可出诊的排班（今日时段已结束或尚未排班）。
      </p>
      <p v-if="activeOrder" class="locked-hint">当前患者完成接诊前，不可切换排班或继续叫号。</p>
    </section>

    <section v-if="activeOrder" class="active-panel" aria-labelledby="active-heading">
      <div class="active-heading">
        <div>
          <span class="active-indicator"><i aria-hidden="true" />当前就诊</span>
          <h2 id="active-heading" class="active-patient">{{ activeOrder.patientName }}</h2>
        </div>
        <OrderStatusTag :status="activeOrder.status" />
      </div>
      <dl class="active-details">
        <div><dt>订单号</dt><dd>{{ activeOrder.orderNo }}</dd></div>
        <div><dt>就诊日期</dt><dd>{{ activeOrder.workDate }}</dd></div>
      </dl>
      <div class="active-actions">
        <p>可填写报告，完成后点击「完成接诊」继续叫号。</p>
        <div class="active-actions-buttons">
          <ElButton :disabled="calling || completing" @click="reportOpen = true">填写报告</ElButton>
          <ElButton type="primary" :loading="completing" :disabled="calling" @click="completeConsultation">完成接诊</ElButton>
        </div>
      </div>
    </section>

    <section class="queue-panel" aria-labelledby="queue-heading" :aria-busy="loading">
      <div class="queue-header">
        <div>
          <span class="section-kicker">WAITING QUEUE</span>
          <h2 id="queue-heading">候诊队列</h2>
        </div>
        <div class="queue-tools">
          <span v-if="waiting && scheduleId !== undefined" class="queue-count">{{ waiting.length }} 人候诊</span>
          <ElButton text :disabled="scheduleId === undefined || loading" @click="retryQueue">刷新队列</ElButton>
        </div>
      </div>

      <div v-if="scheduleId === undefined" class="guidance-state" role="status">
        <span class="guidance-icon" aria-hidden="true">候</span>
        <div><strong>请先选择出诊排班</strong><p>选择后将按服务端签到顺序显示候诊患者。</p></div>
      </div>
      <div v-else-if="loading" class="loading-queue" role="status" aria-label="正在加载候诊队列">
        <ElSkeleton :rows="3" animated />
        <ElSkeleton :rows="3" animated />
      </div>
      <div v-else-if="error" class="queue-error">
        <ErrorState :message="error" @retry="retryQueue" />
      </div>
      <div v-else-if="!waiting?.length" class="empty-queue">
        <EmptyState description="暂无候诊患者" />
      </div>
      <ol v-else class="queue-list" aria-label="候诊患者列表">
        <li v-for="patient in waiting" :key="patient.orderId" class="queue-row">
          <span class="queue-seq" :aria-label="`队列序号 ${patient.seq}`">{{ patient.seq }}</span>
          <div class="queue-person"><strong>{{ patient.patientName }}</strong><span>{{ patient.orderNo }}</span></div>
          <div class="sign-time"><span>签到时间</span><strong>{{ patient.signTime }}</strong></div>
        </li>
      </ol>

      <div class="call-bar">
        <p v-if="activeOrder">请先完成当前患者接诊，再叫下一位。</p>
        <p v-else-if="!waiting?.length">队列为空时无法叫号。</p>
        <p v-else>系统将按队列顺序叫下一位患者。</p>
        <ElButton
          type="primary"
          size="large"
          class="call-button"
          :loading="calling"
          :disabled="!canCall"
          @click="callNext"
        >{{ calling ? '正在叫号…' : '叫下一位' }}</ElButton>
      </div>
      <ElAlert
        v-if="callError"
        class="call-error"
        type="error"
        :title="callError"
        show-icon
        :closable="false"
      />
    </section>

    <ReportDrawer
      v-if="activeOrder"
      v-model="reportOpen"
      :order-id="activeOrder.id"
      :patient-name="activeOrder.patientName"
      @saved="reportSaved"
    />
  </div>
</template>

<style scoped>
.workbench-page { width: min(100%, 900px); margin: 0 auto; padding: 24px 16px 36px; }
.workbench-page :deep(.page-header) { margin-bottom: 18px; }
.success-alert, .queue-warning { margin-bottom: 14px; }
.schedule-panel, .active-panel, .queue-panel { border: 1px solid var(--color-border); border-radius: 18px; background: white; box-shadow: 0 10px 30px rgba(18, 57, 74, .04); }
.schedule-panel { display: grid; grid-template-columns: minmax(0, 1fr) minmax(260px, 380px); align-items: end; gap: 18px 28px; padding: 22px 24px; }
.section-heading, .queue-header, .active-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-kicker { color: var(--color-brand); font-size: 9px; font-weight: 800; letter-spacing: .16em; }
.section-heading h2, .queue-header h2 { margin: 5px 0 0; color: var(--color-brand-deep); font-size: 19px; }
.session-date { padding-bottom: 10px; color: var(--color-muted); font-size: 12px; white-space: nowrap; }
.schedule-field { display: grid; gap: 8px; color: var(--color-text); font-size: 13px; font-weight: 700; }
.schedule-field :deep(.el-select) { width: 100%; }
.schedule-field :deep(.el-select__wrapper) { min-height: 44px; border-radius: 10px; }
.schedule-notice { grid-column: 1 / -1; margin: -4px 0 0; color: #8b641f; font-size: 12px; line-height: 1.6; }
.locked-hint { grid-column: 1 / -1; margin: -4px 0 0; color: #8b641f; font-size: 12px; line-height: 1.6; }
.active-panel { margin-top: 18px; padding: 22px 24px; border-color: #a9d6cf; background: linear-gradient(120deg, #f4fbf9, white 72%); }
.active-indicator { display: inline-flex; align-items: center; gap: 7px; color: var(--color-brand); font-size: 12px; font-weight: 700; }
.active-indicator i { width: 8px; height: 8px; border-radius: 50%; background: var(--color-brand); box-shadow: 0 0 0 4px rgba(8, 127, 122, .1); }
.active-patient { margin: 8px 0 0; color: var(--color-brand-deep); font-size: 27px; }
.active-details { display: flex; gap: 42px; margin: 18px 0 0; }
.active-details > div { display: grid; gap: 5px; }
.active-details dt { color: var(--color-muted); font-size: 12px; }
.active-details dd { margin: 0; font-size: 14px; font-weight: 700; overflow-wrap: anywhere; }
.active-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 20px; padding-top: 16px; border-top: 1px solid #d9ebe7; }
.active-actions p { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.6; }
.active-actions-buttons { display: flex; flex: none; gap: 10px; }
.active-actions :deep(.el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
.queue-panel { margin-top: 18px; overflow: hidden; }
.queue-header { align-items: center; padding: 22px 24px 18px; border-bottom: 1px solid #edf2f4; }
.queue-tools { display: flex; align-items: center; gap: 12px; }
.queue-count { color: var(--color-muted); font-size: 12px; white-space: nowrap; }
.queue-tools :deep(.el-button) { min-height: 40px; }
.guidance-state, .empty-queue { display: flex; min-height: 200px; align-items: center; justify-content: center; gap: 16px; padding: 32px 24px; }
.guidance-state { justify-content: flex-start; }
.guidance-icon { display: grid; width: 48px; height: 48px; flex: 0 0 auto; place-items: center; border-radius: 14px; background: var(--color-brand-soft); color: var(--color-brand); font-weight: 800; }
.guidance-state strong { color: var(--color-brand-deep); font-size: 15px; }
.guidance-state p { margin: 7px 0 0; color: var(--color-muted); font-size: 13px; line-height: 1.6; }
.loading-queue { display: grid; gap: 20px; padding: 24px; }
.queue-error { padding: 24px; }
.queue-list { display: grid; margin: 0; padding: 0; list-style: none; }
.queue-row { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; align-items: center; gap: 16px; min-height: 88px; padding: 16px 24px; border-bottom: 1px solid #edf2f4; }
.queue-row:last-child { border-bottom: 0; }
.queue-seq { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 12px; background: var(--color-brand-soft); color: var(--color-brand); font-size: 18px; font-weight: 800; font-variant-numeric: tabular-nums; }
.queue-person, .sign-time { display: grid; gap: 6px; min-width: 0; }
.queue-person strong { color: var(--color-brand-deep); font-size: 16px; }
.queue-person span, .sign-time span { color: var(--color-muted); font-size: 11px; }
.sign-time { justify-items: end; text-align: right; }
.sign-time strong { color: var(--color-text); font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
.call-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 24px; border-top: 1px solid #edf2f4; background: #fafcfd; }
.call-bar p { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.6; }
.call-bar :deep(.call-button.el-button) { min-width: 150px; min-height: 48px; border-radius: 11px; font-weight: 700; }
.call-error { margin: 0 24px 20px; }
@media (max-width: 680px) {
  .workbench-page { padding: 20px 14px 30px; }
  .workbench-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .schedule-panel { grid-template-columns: 1fr; align-items: stretch; padding: 20px 18px; }
  .session-date { padding: 0; }
  .active-panel { padding: 20px 18px; }
  .active-details { gap: 24px; }
  .active-actions { align-items: stretch; flex-direction: column; }
  .queue-header { padding: 20px 18px 16px; }
  .queue-tools { gap: 6px; }
  .queue-row { grid-template-columns: 42px minmax(0, 1fr); gap: 12px; padding: 16px 18px; }
  .sign-time { grid-column: 2; justify-items: start; gap: 3px; text-align: left; }
  .call-bar { align-items: stretch; flex-direction: column; padding: 16px 18px; }
  .call-bar :deep(.call-button.el-button) { width: 100%; }
  .call-error { margin: 0 18px 18px; }
}
</style>
