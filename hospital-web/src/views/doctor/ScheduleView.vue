<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElSkeleton } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { doctorWorkApi } from '@/api/doctorWork'
import { useAsync } from '@/composables/useAsync'
import type { Schedule, TimeSlot } from '@/types/models'

const slotText: Record<TimeSlot, { label: string; range: string }> = {
  1: { label: '上午', range: '08:00—12:00' },
  2: { label: '下午', range: '13:00—17:00' },
  3: { label: '晚间', range: '18:00—21:00' }
}

const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const router = useRouter()
const { data, loading, error, reload } = useAsync(() => doctorWorkApi.schedules())
const schedules = computed<Schedule[]>(() => data.value ?? [])
void reload()

function todayString() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

// 分成「今天」「后续」两组：医生打开页面最先关心今天还剩几个号
const groups = computed(() => {
  const today = todayString()
  const todayList = schedules.value.filter((schedule) => schedule.workDate === today)
  const laterList = schedules.value.filter((schedule) => schedule.workDate !== today)
  return [
    { key: 'today', title: '今天', items: todayList },
    { key: 'later', title: '后续排班', items: laterList }
  ].filter((group) => group.items.length > 0)
})

function monthDay(workDate: string) {
  const [, month, day] = workDate.split('-').map(Number)
  return { month, day }
}

function weekday(workDate: string) {
  const [year, month, day] = workDate.split('-').map(Number)
  return weekdays[new Date(year, month - 1, day).getDay()]
}

function sessionStatus(schedule: Schedule) {
  if (schedule.status !== 1) return { text: '已停诊', tone: 'stopped' }
  if (schedule.leftNum <= 0) return { text: '已约满', tone: 'full' }
  return { text: '出诊中', tone: 'running' }
}

function enterWorkbench() {
  void router.push('/doctor/workbench')
}
</script>

<template>
  <div class="schedule-page">
    <PageHeader title="我的排班" subtitle="今日未结束的时段与后续排班，进入工作台即可叫号接诊" />

    <section v-if="loading && !schedules.length" class="panel" aria-busy="true" aria-label="正在加载排班">
      <div class="loading-block"><ElSkeleton :rows="3" animated /><ElSkeleton :rows="3" animated /></div>
    </section>

    <section v-else-if="error" class="panel error-block">
      <ErrorState :message="`排班加载失败：${error}`" @retry="reload" />
    </section>

    <section v-else-if="!schedules.length" class="panel empty-block">
      <EmptyState description="暂无排班，等待管理员放号后再查看" />
    </section>

    <div v-else class="day-groups">
      <section v-for="group in groups" :key="group.key" class="day-group" :aria-labelledby="`group-${group.key}`">
        <div class="day-heading">
          <h2 :id="`group-${group.key}`">{{ group.title }}</h2>
          <span>{{ group.items.length }} 个时段</span>
        </div>
        <ul class="schedule-list">
          <li v-for="schedule in group.items" :key="schedule.id" class="schedule-card">
            <div class="date-badge" :aria-label="`${schedule.workDate} ${weekday(schedule.workDate)}`">
              <strong>{{ monthDay(schedule.workDate).day }}</strong>
              <span>{{ monthDay(schedule.workDate).month }}月 · {{ weekday(schedule.workDate) }}</span>
            </div>
            <div class="session-info">
              <div class="session-title">
                <strong>{{ slotText[schedule.timeSlot].label }}</strong>
                <span class="session-range">{{ slotText[schedule.timeSlot].range }}</span>
                <span class="status-tag" :class="`is-${sessionStatus(schedule).tone}`">{{ sessionStatus(schedule).text }}</span>
              </div>
              <dl class="session-metrics">
                <div><dt>剩余号源</dt><dd>{{ schedule.leftNum }} / {{ schedule.totalNum }}</dd></div>
                <div><dt>挂号费</dt><dd>¥{{ schedule.price }}</dd></div>
              </dl>
            </div>
            <div class="session-action">
              <ElButton type="primary" :disabled="schedule.status !== 1" @click="enterWorkbench">进入工作台</ElButton>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.schedule-page { width: min(100%, 900px); margin: 0 auto; padding: 24px 16px 36px; }
.schedule-page :deep(.page-header) { margin-bottom: 18px; }
.panel { border: 1px solid var(--color-border); border-radius: 18px; background: white; box-shadow: 0 10px 30px rgba(18, 57, 74, .04); }
.loading-block { display: grid; gap: 20px; padding: 24px; }
.error-block, .empty-block { padding: 24px; }
.day-group { margin-top: 18px; }
.day-group:first-child { margin-top: 0; }
.day-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin: 0 4px 12px; }
.day-heading h2 { margin: 0; color: var(--color-brand-deep); font-size: 16px; }
.day-heading span { color: var(--color-muted); font-size: 12px; }
.schedule-list { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
.schedule-card { display: grid; grid-template-columns: 88px minmax(0, 1fr) auto; align-items: center; gap: 20px; padding: 18px 22px; border: 1px solid var(--color-border); border-radius: 18px; background: white; box-shadow: 0 10px 30px rgba(18, 57, 74, .04); }
.date-badge { display: grid; justify-items: center; gap: 2px; padding: 10px 0; border-radius: 14px; background: var(--color-brand-soft); }
.date-badge strong { color: var(--color-brand-deep); font-size: 26px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.date-badge span { color: var(--color-brand); font-size: 11px; font-weight: 700; }
.session-info { display: grid; gap: 12px; min-width: 0; }
.session-title { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.session-title strong { color: var(--color-brand-deep); font-size: 17px; }
.session-range { color: var(--color-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.status-tag { padding: 3px 9px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.status-tag.is-running { background: #e7f6f2; color: #0b7d6f; }
.status-tag.is-full { background: #fdf1e3; color: #9a6415; }
.status-tag.is-stopped { background: #f1f3f5; color: #7b8a92; }
.session-metrics { display: flex; gap: 36px; margin: 0; }
.session-metrics > div { display: grid; gap: 4px; }
.session-metrics dt { color: var(--color-muted); font-size: 11px; }
.session-metrics dd { margin: 0; color: var(--color-text); font-size: 14px; font-weight: 700; font-variant-numeric: tabular-nums; }
.session-action :deep(.el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
@media (max-width: 680px) {
  .schedule-page { padding: 20px 14px 30px; }
  .schedule-card { grid-template-columns: 72px minmax(0, 1fr); gap: 14px; padding: 16px 16px 18px; }
  .session-action { grid-column: 1 / -1; }
  .session-action :deep(.el-button) { width: 100%; }
  .session-metrics { gap: 24px; }
}
</style>
