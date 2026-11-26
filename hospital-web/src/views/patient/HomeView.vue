<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElSkeleton } from 'element-plus'
import { departmentApi } from '@/api/department'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { data: departments, loading, error, reload } = useAsync(() => departmentApi.list())

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => auth.user?.username || '患者')

const quickLinks = [
  { title: '预约挂号', description: '选择科室与医生', path: '/patient/departments', icon: 'calendar' },
  { title: '我的订单', description: '查看挂号与支付状态', path: '/patient/orders', icon: 'order' },
  { title: '报告查询', description: '查看检验检查报告', path: '/patient/reports', icon: 'report' }
]

onMounted(reload)
</script>

<template>
  <div class="patient-home">
    <PageHeader :title="`${greeting}，${displayName}`" subtitle="愿您今天健康安心，需要时我们随时为您服务。" />

    <section class="triage-entry" aria-labelledby="triage-title">
      <div class="triage-copy">
        <span class="triage-label">不确定该挂哪个科？</span>
        <h2 id="triage-title">描述症状，获取就诊建议</h2>
        <p>告诉我们您的不适，帮助您找到合适的科室。</p>
        <RouterLink to="/patient/triage" class="triage-action">开始症状描述</RouterLink>
      </div>
      <svg class="triage-icon" viewBox="0 0 64 64" aria-hidden="true">
        <path d="M25 10h14v15h15v14H39v15H25V39H10V25h15V10Z" />
        <circle cx="32" cy="32" r="29" />
      </svg>
    </section>

    <section class="quick-section" aria-labelledby="quick-title">
      <div class="section-heading">
        <h2 id="quick-title">就诊服务</h2>
      </div>
      <nav class="quick-links" aria-label="患者快捷服务">
        <RouterLink v-for="link in quickLinks" :key="link.path" :to="link.path" class="quick-link">
          <span class="quick-icon" :class="`quick-icon-${link.icon}`" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path v-if="link.icon === 'calendar'" d="M6 3v3m12-3v3M4 8h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Zm3 8h3m2 0h3m-8 4h3" />
              <path v-else-if="link.icon === 'order'" d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 6h6m-6 4h6" />
              <path v-else d="M7 3h10v18H7V3Zm3 5h4m-4 4h4m-4 4h3" />
            </svg>
          </span>
          <span class="quick-copy"><strong>{{ link.title }}</strong><span>{{ link.description }}</span></span>
        </RouterLink>
      </nav>
    </section>

    <section class="department-section" aria-labelledby="department-title" :aria-busy="loading">
      <div class="section-heading department-heading">
        <div>
          <h2 id="department-title">选择科室</h2>
          <p>查看科室介绍，进入医生列表预约挂号</p>
        </div>
      </div>

      <div v-if="loading" class="department-loading" role="status" aria-label="正在加载科室">
        <ElSkeleton :rows="2" animated />
        <ElSkeleton :rows="2" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <EmptyState v-else-if="!departments?.length" description="暂无科室信息，请稍后再试" />
      <div v-else class="department-list">
        <RouterLink
          v-for="department in departments"
          :key="department.id"
          :to="`/patient/doctors?departmentId=${department.id}`"
          class="department-item"
        >
          <span class="department-mark" aria-hidden="true">{{ department.name.slice(0, 1) }}</span>
          <span class="department-copy">
            <strong>{{ department.name }}</strong>
            <span>{{ department.description || '暂无科室简介' }}</span>
          </span>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.patient-home { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.patient-home :deep(.page-header) { margin-bottom: 20px; }
.patient-home :deep(.page-header h1) { overflow-wrap: anywhere; }
.triage-entry {
  position: relative;
  display: flex;
  min-height: 194px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  overflow: hidden;
  padding: 26px;
  border-radius: 20px;
  background: var(--color-brand-deep);
  color: white;
}
.triage-copy { position: relative; z-index: 1; max-width: 430px; }
.triage-label { color: #bce4df; font-size: 13px; }
.triage-copy h2 { margin: 8px 0; font-size: 23px; line-height: 1.4; }
.triage-copy p { margin: 0 0 20px; color: #d8e8eb; font-size: 14px; line-height: 1.7; }
.triage-action {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border-radius: 10px;
  background: white;
  color: var(--color-brand-deep);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}
.triage-action:hover { background: var(--color-brand-soft); }
.triage-icon { width: 108px; flex: 0 0 auto; fill: none; stroke: #71bdb4; stroke-width: 1.5; }
.quick-section { margin-top: 28px; }
.section-heading { margin-bottom: 14px; }
.section-heading h2 { margin: 0; color: var(--color-brand-deep); font-size: 19px; }
.department-heading p { margin: 6px 0 0; color: var(--color-muted); font-size: 13px; }
.quick-links { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.quick-link {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 18px 16px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: white;
  color: var(--color-text);
  text-decoration: none;
}
.quick-link:hover { border-color: var(--color-brand); }
.quick-icon { display: grid; width: 40px; height: 40px; flex: 0 0 auto; place-items: center; border-radius: 12px; background: var(--color-brand-soft); color: var(--color-brand); }
.quick-icon svg { width: 23px; height: 23px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.quick-icon-order { background: #eaf1f8; color: #356a95; }
.quick-icon-report { background: #eef3e5; color: #58723e; }
.quick-copy { display: grid; min-width: 0; gap: 5px; }
.quick-copy strong { font-size: 14px; }
.quick-copy > span { color: var(--color-muted); font-size: 12px; line-height: 1.5; }
.department-section { margin-top: 30px; }
.department-loading { display: grid; gap: 16px; padding: 22px; border: 1px solid var(--color-border); border-radius: 14px; background: white; }
.department-list { display: grid; gap: 10px; }
.department-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: white;
  color: var(--color-text);
  text-decoration: none;
}
.department-item:hover { border-color: var(--color-brand); }
.department-mark { display: grid; width: 44px; height: 44px; flex: 0 0 auto; place-items: center; border-radius: 12px; background: var(--color-brand-soft); color: var(--color-brand-deep); font-size: 17px; font-weight: 700; }
.department-copy { display: grid; min-width: 0; flex: 1; gap: 6px; }
.department-copy strong { font-size: 16px; }
.department-copy > span { overflow: hidden; color: var(--color-muted); font-size: 13px; line-height: 1.6; text-overflow: ellipsis; white-space: nowrap; }
.department-item > svg { width: 18px; flex: 0 0 auto; fill: none; stroke: var(--color-muted); stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }

@media (max-width: 560px) {
  .patient-home { padding: 20px 14px 28px; }
  .triage-entry { min-height: 200px; align-items: flex-start; padding: 22px; }
  .triage-copy { max-width: calc(100% - 38px); }
  .triage-copy h2 { max-width: 260px; font-size: 21px; }
  .triage-copy p { max-width: 245px; font-size: 13px; }
  .triage-icon { position: absolute; right: -25px; bottom: -18px; width: 100px; opacity: 0.45; }
  .quick-links { gap: 8px; }
  .quick-link { flex-direction: column; align-items: flex-start; gap: 10px; padding: 14px 12px; }
  .quick-copy > span { display: none; }
  .department-item { padding: 15px 14px; }
}

@media (min-width: 760px) {
  .patient-home { padding-top: 32px; }
  .triage-entry { padding: 30px 34px; }
}
</style>
