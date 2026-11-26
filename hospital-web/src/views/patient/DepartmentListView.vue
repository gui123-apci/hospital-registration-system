<script setup lang="ts">
import { onMounted } from 'vue'
import { ElSkeleton } from 'element-plus'
import { departmentApi } from '@/api/department'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'

const { data: departments, loading, error, reload } = useAsync(() => departmentApi.list())

onMounted(reload)
</script>

<template>
  <div class="selection-page">
    <PageHeader title="选择科室" subtitle="请选择就诊科室，查看该科室的出诊医生">
      <RouterLink to="/patient/home" class="back-link">返回首页</RouterLink>
    </PageHeader>

    <section class="selection-card" aria-label="科室列表" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载科室">
        <ElSkeleton v-for="item in 3" :key="item" :rows="2" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <div v-else-if="!departments?.length" class="state-with-action">
        <EmptyState description="暂无科室信息，请稍后再试" />
        <button type="button" class="secondary-button" @click="reload">重新加载</button>
      </div>
      <div v-else class="department-grid">
        <RouterLink
          v-for="department in departments"
          :key="department.id"
          :to="{ path: '/patient/doctors', query: { departmentId: department.id } }"
          class="department-card"
        >
          <span class="department-mark" aria-hidden="true">{{ department.name.slice(0, 1) }}</span>
          <span class="department-copy">
            <strong>{{ department.name }}</strong>
            <span>{{ department.description || '暂无科室简介' }}</span>
          </span>
          <span class="card-action" aria-hidden="true">选择</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.selection-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.selection-page :deep(.page-header) { margin-bottom: 20px; }
.selection-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.selection-card { padding: 16px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.loading-list { display: grid; gap: 18px; padding: 8px; }
.department-grid { display: grid; gap: 10px; }
.department-card { display: flex; min-height: 84px; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--color-border); border-radius: 13px; color: var(--color-text); text-decoration: none; transition: border-color .2s, background .2s; }
.department-card:hover { border-color: var(--color-brand); background: #f8fcfb; }
.department-mark { display: grid; width: 46px; height: 46px; flex: 0 0 auto; place-items: center; border-radius: 13px; background: var(--color-brand-soft); color: var(--color-brand-deep); font-size: 18px; font-weight: 700; }
.department-copy { display: grid; min-width: 0; flex: 1; gap: 6px; }
.department-copy strong { font-size: 16px; }
.department-copy > span { display: -webkit-box; overflow: hidden; color: var(--color-muted); font-size: 13px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.card-action { color: var(--color-brand); font-size: 13px; font-weight: 700; }
.state-with-action { display: grid; justify-items: center; gap: 4px; padding: 12px 0 20px; }
.secondary-button { min-height: 40px; padding: 8px 18px; border: 1px solid var(--color-brand); border-radius: 10px; background: white; color: var(--color-brand); cursor: pointer; }
@media (max-width: 560px) {
  .selection-page { padding: 20px 14px 28px; }
  .selection-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .selection-card { padding: 12px; }
  .department-card { padding: 14px 12px; }
  .card-action { font-size: 12px; }
}
@media (min-width: 640px) {
  .department-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
