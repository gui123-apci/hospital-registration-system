<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElSkeleton } from 'element-plus'
import { doctorApi } from '@/api/doctor'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'

const route = useRoute()

function readPositiveId(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

const { data: doctors, loading, error, reload } = useAsync(async () => {
  const departmentId = readPositiveId(route.query.departmentId)
  if (departmentId === null) throw new Error('链接中的科室信息无效，请返回重新选择科室')
  return doctorApi.list(departmentId)
})

onMounted(reload)
</script>

<template>
  <div class="selection-page">
    <PageHeader title="选择医生" subtitle="查看医生职称与简介，选择合适的出诊医生">
      <RouterLink to="/patient/departments" class="back-link">重新选科</RouterLink>
    </PageHeader>

    <section class="doctor-list" aria-label="医生列表" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载医生">
        <ElSkeleton v-for="item in 3" :key="item" :rows="3" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <div v-else-if="!doctors?.length" class="state-with-action">
        <EmptyState description="该科室暂无出诊医生，请选择其他科室" />
        <RouterLink to="/patient/departments" class="secondary-button">返回科室列表</RouterLink>
      </div>
      <RouterLink
        v-for="doctor in doctors"
        :key="doctor.id"
        :to="{ path: '/patient/schedules', query: { doctorId: doctor.id, departmentId: doctor.departmentId } }"
        class="doctor-card"
      >
        <span class="doctor-avatar" aria-hidden="true">{{ doctor.name.slice(0, 1) }}</span>
        <span class="doctor-copy">
          <span class="doctor-heading">
            <strong>{{ doctor.name }}</strong>
            <span class="doctor-title">{{ doctor.title || '职称待完善' }}</span>
          </span>
          <span class="doctor-intro">{{ doctor.intro || '暂无医生简介' }}</span>
          <span class="doctor-action">查看可预约排班</span>
        </span>
        <svg class="chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.selection-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.selection-page :deep(.page-header) { margin-bottom: 20px; }
.selection-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.doctor-list { display: grid; gap: 12px; }
.loading-list { display: grid; gap: 18px; padding: 20px; border: 1px solid var(--color-border); border-radius: 15px; background: white; }
.doctor-card { display: flex; align-items: flex-start; gap: 14px; padding: 19px 17px; border: 1px solid var(--color-border); border-radius: 15px; background: white; color: var(--color-text); text-decoration: none; transition: border-color .2s, box-shadow .2s; }
.doctor-card:hover { border-color: var(--color-brand); box-shadow: 0 6px 20px rgba(18, 57, 74, .06); }
.doctor-avatar { display: grid; width: 52px; height: 52px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: var(--color-brand-soft); color: var(--color-brand-deep); font-size: 20px; font-weight: 700; }
.doctor-copy { display: grid; min-width: 0; flex: 1; gap: 9px; }
.doctor-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
.doctor-heading strong { font-size: 17px; }
.doctor-title { padding: 3px 8px; border-radius: 6px; background: #eef5f8; color: #356a7c; font-size: 12px; }
.doctor-intro { color: var(--color-muted); font-size: 13px; line-height: 1.7; }
.doctor-action { color: var(--color-brand); font-size: 13px; font-weight: 700; }
.chevron { width: 18px; height: 18px; align-self: center; flex: 0 0 auto; fill: none; stroke: var(--color-muted); stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.state-with-action { display: grid; justify-items: center; gap: 4px; padding: 12px 0 20px; border: 1px solid var(--color-border); border-radius: 15px; background: white; }
.secondary-button { display: inline-flex; min-height: 40px; align-items: center; padding: 8px 18px; border: 1px solid var(--color-brand); border-radius: 10px; color: var(--color-brand); text-decoration: none; }
@media (max-width: 560px) {
  .selection-page { padding: 20px 14px 28px; }
  .selection-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .doctor-card { padding: 16px 14px; }
  .doctor-avatar { width: 46px; height: 46px; font-size: 18px; }
}
</style>
