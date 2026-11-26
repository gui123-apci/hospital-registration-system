<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElSkeleton } from 'element-plus'
import { reportApi } from '@/api/report'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'

const { data, loading, error, reload } = useAsync(() => reportApi.list())
const reports = computed(() => data.value ?? [])
const expandedIds = ref<number[]>([])

function isExpanded(id: number) {
  return expandedIds.value.includes(id)
}

function toggleReport(id: number) {
  expandedIds.value = isExpanded(id)
    ? expandedIds.value.filter((expandedId) => expandedId !== id)
    : [...expandedIds.value, id]
}

function formatReportTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(reload)
</script>

<template>
  <div class="reports-page">
    <PageHeader title="我的报告" subtitle="查看检验检查结果，点击报告阅读完整内容" />

    <section class="reports-panel" aria-label="报告列表" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载报告">
        <ElSkeleton :rows="5" animated />
        <ElSkeleton :rows="5" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <EmptyState v-else-if="!reports.length" description="暂无报告，完成检验检查后可在这里查看" />
      <div v-else class="report-list">
        <article v-for="report in reports" :key="report.id" class="report-card">
          <div class="report-heading">
            <span class="report-type">{{ report.reportType || '未分类' }}</span>
            <h2>{{ report.title }}</h2>
          </div>

          <dl class="report-meta">
            <div>
              <dt>就诊人</dt>
              <dd>{{ report.patientName }}</dd>
            </div>
            <div>
              <dt>报告时间</dt>
              <dd>{{ formatReportTime(report.reportTime) }}</dd>
            </div>
          </dl>

          <button
            type="button"
            class="report-toggle"
            :aria-expanded="isExpanded(report.id)"
            :aria-controls="`report-content-${report.id}`"
            @click="toggleReport(report.id)"
          >
            <span>{{ isExpanded(report.id) ? '收起全文' : '查看全文' }}</span>
            <svg viewBox="0 0 24 24" :class="{ 'is-expanded': isExpanded(report.id) }" aria-hidden="true">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </button>

          <div
            v-if="isExpanded(report.id)"
            :id="`report-content-${report.id}`"
            class="report-content"
            role="region"
            :aria-label="`${report.title}完整内容`"
          >
            {{ report.content || '暂无可查看的报告内容' }}
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.reports-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.reports-page :deep(.page-header) { margin-bottom: 20px; }
.reports-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.reports-panel { min-height: 180px; }
.loading-list { display: grid; gap: 22px; padding: 22px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.report-list { display: grid; gap: 14px; }
.report-card { overflow: hidden; border: 1px solid var(--color-border); border-radius: 16px; background: white; box-shadow: 0 8px 24px rgba(18, 57, 74, .04); }
.report-heading { padding: 18px 18px 0; }
.report-type { display: inline-flex; padding: 3px 8px; border-radius: 6px; background: #eef3e5; color: #58723e; font-size: 12px; }
.report-heading h2 { margin: 10px 0 0; overflow-wrap: anywhere; color: var(--color-brand-deep); font-size: 17px; line-height: 1.5; }
.report-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 20px; margin: 16px 18px 0; }
.report-meta > div { display: grid; min-width: 0; gap: 5px; }
.report-meta dt { color: var(--color-muted); font-size: 12px; }
.report-meta dd { margin: 0; overflow-wrap: anywhere; font-size: 14px; font-weight: 600; line-height: 1.5; }
.report-toggle {
  display: flex;
  width: calc(100% - 36px);
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 18px 0;
  padding: 10px 0;
  border: 0;
  border-top: 1px solid #edf2f4;
  background: transparent;
  color: var(--color-brand);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.report-toggle svg { width: 18px; height: 18px; flex: 0 0 auto; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; transition: transform .18s ease; }
.report-toggle svg.is-expanded { transform: rotate(180deg); }
.report-content { margin-top: 2px; padding: 18px; border-top: 1px solid #edf2f4; background: #f8fbfc; color: var(--color-text); font-size: 14px; line-height: 1.9; overflow-wrap: anywhere; white-space: pre-wrap; }
@media (max-width: 560px) {
  .reports-page { padding: 20px 14px 28px; }
  .report-heading { padding: 16px 14px 0; }
  .report-meta { grid-template-columns: 1fr; margin: 14px 14px 0; }
  .report-toggle { width: calc(100% - 28px); margin-right: 14px; margin-left: 14px; }
  .report-content { padding: 16px 14px; }
}
</style>
