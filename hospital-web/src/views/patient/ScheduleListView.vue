<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElSkeleton } from 'element-plus'
import { scheduleApi } from '@/api/schedule'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'
import { timeSlotLabel } from '@/utils/schedule'
import type { Schedule } from '@/types/models'

const route = useRoute()
const router = useRouter()

function readPositiveId(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

const { data: schedules, loading, error, reload } = useAsync(async () => {
  const doctorId = readPositiveId(route.query.doctorId)
  if (doctorId === null) throw new Error('链接中的医生信息无效，请返回重新选择医生')
  return scheduleApi.list(doctorId)
})

const backToDoctors = computed(() => {
  const departmentId = readPositiveId(route.query.departmentId)
  return {
    path: departmentId === null ? '/patient/departments' : '/patient/doctors',
    query: departmentId === null ? {} : { departmentId }
  }
})

const scheduleGroups = computed(() => {
  const grouped = new Map<string, Schedule[]>()
  const sorted = [...(schedules.value ?? [])].sort((a, b) =>
    a.workDate.localeCompare(b.workDate) || a.timeSlot - b.timeSlot
  )
  for (const schedule of sorted) {
    const group = grouped.get(schedule.workDate) ?? []
    group.push(schedule)
    grouped.set(schedule.workDate, group)
  }
  return Array.from(grouped, ([workDate, items]) => ({ workDate, schedules: items }))
})

function canBook(schedule: Schedule) {
  return schedule.status === 1 && schedule.leftNum > 0
}

function availabilityLabel(schedule: Schedule) {
  if (schedule.status !== 1) return '暂不可约'
  if (schedule.leftNum <= 0) return '已约满'
  return '预约挂号'
}

function book(schedule: Schedule) {
  const doctorId = readPositiveId(route.query.doctorId)
  if (!canBook(schedule) || doctorId === null) return
  router.push({
    path: '/patient/booking',
    query: {
      scheduleId: schedule.id,
      doctorId,
      departmentId: schedule.departmentId
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="selection-page">
    <PageHeader title="选择排班" subtitle="按日期查看出诊时段、剩余号源与挂号费用">
      <RouterLink :to="backToDoctors" class="back-link">重新选医生</RouterLink>
    </PageHeader>

    <section class="schedule-list" aria-label="可预约排班" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载排班">
        <ElSkeleton v-for="item in 3" :key="item" :rows="3" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <div v-else-if="!scheduleGroups.length" class="state-with-action">
        <EmptyState description="该医生近期暂无可预约排班" />
        <RouterLink :to="backToDoctors" class="secondary-button">返回医生列表</RouterLink>
      </div>
      <template v-else>
        <section v-for="group in scheduleGroups" :key="group.workDate" class="date-group" :aria-label="`${group.workDate} 排班`">
          <h2 class="date-heading">{{ group.workDate }}</h2>
          <button
            v-for="schedule in group.schedules"
            :key="schedule.id"
            type="button"
            class="schedule-card"
            :disabled="!canBook(schedule)"
            :aria-label="`${schedule.workDate} ${timeSlotLabel(schedule.timeSlot)}，剩余 ${schedule.leftNum} 个号源，${schedule.price.toFixed(2)} 元，${availabilityLabel(schedule)}`"
            @click="book(schedule)"
          >
            <span class="slot-block">
              <strong>{{ timeSlotLabel(schedule.timeSlot) }}</strong>
              <span>出诊时段</span>
            </span>
            <span class="schedule-copy">
              <span class="availability" :class="{ 'is-unavailable': !canBook(schedule) }">
                {{ canBook(schedule) ? `剩余 ${schedule.leftNum} 个号源` : availabilityLabel(schedule) }}
              </span>
              <span class="price"><small>¥</small>{{ schedule.price.toFixed(2) }}</span>
            </span>
            <span class="booking-action" :class="{ 'is-disabled': !canBook(schedule) }">{{ availabilityLabel(schedule) }}</span>
          </button>
        </section>
      </template>
    </section>
  </div>
</template>

<style scoped>
.selection-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.selection-page :deep(.page-header) { margin-bottom: 20px; }
.selection-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.schedule-list { display: grid; gap: 18px; }
.loading-list { display: grid; gap: 18px; padding: 20px; border: 1px solid var(--color-border); border-radius: 15px; background: white; }
.date-group { display: grid; gap: 9px; }
.date-heading { margin: 0 0 2px 2px; color: var(--color-brand-deep); font-size: 15px; }
.schedule-card { display: flex; width: 100%; min-height: 94px; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--color-border); border-radius: 14px; background: white; color: var(--color-text); text-align: left; cursor: pointer; transition: border-color .2s, box-shadow .2s; }
.schedule-card:not(:disabled):hover { border-color: var(--color-brand); box-shadow: 0 6px 20px rgba(18, 57, 74, .06); }
.schedule-card:disabled { background: #f7f9fa; cursor: not-allowed; opacity: .72; }
.slot-block { display: grid; width: 70px; flex: 0 0 auto; gap: 4px; padding: 10px 6px; border-radius: 10px; background: var(--color-brand-soft); color: var(--color-brand-deep); text-align: center; }
.slot-block strong { font-size: 16px; }
.slot-block span { color: var(--color-muted); font-size: 11px; }
.schedule-copy { display: grid; min-width: 0; flex: 1; gap: 7px; }
.availability { color: var(--color-brand); font-size: 13px; }
.availability.is-unavailable { color: var(--color-muted); }
.price { color: #b05b36; font-size: 21px; font-weight: 700; }
.price small { margin-right: 2px; font-size: 13px; }
.booking-action { padding: 8px 12px; border-radius: 8px; background: var(--color-brand); color: white; font-size: 13px; font-weight: 700; white-space: nowrap; }
.booking-action.is-disabled { background: #e5eaed; color: var(--color-muted); }
.state-with-action { display: grid; justify-items: center; gap: 4px; padding: 12px 0 20px; border: 1px solid var(--color-border); border-radius: 15px; background: white; }
.secondary-button { display: inline-flex; min-height: 40px; align-items: center; padding: 8px 18px; border: 1px solid var(--color-brand); border-radius: 10px; color: var(--color-brand); text-decoration: none; }
@media (max-width: 560px) {
  .selection-page { padding: 20px 14px 28px; }
  .selection-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .schedule-card { gap: 10px; padding: 14px 12px; }
  .slot-block { width: 62px; }
  .booking-action { padding: 8px 9px; font-size: 12px; }
}
</style>
