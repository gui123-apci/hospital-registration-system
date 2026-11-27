<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElSkeleton } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { dashboardApi, type DashboardData } from '@/api/dashboard'
import PageHeader from '@/components/PageHeader.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useAsync } from '@/composables/useAsync'

use([BarChart, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const { data, loading, error, reload } = useAsync(() => dashboardApi.overview())
const dashboard = computed<DashboardData | null>(() => data.value ?? null)

const statusLabels: Record<number, string> = {
  0: '待支付', 1: '已支付', 2: '已退号', 3: '已就诊', 4: '已取消', 5: '已爽约', 6: '就诊中'
}

const statusChartOption = computed(() => {
  if (!dashboard.value) return {}
  const dist = dashboard.value.statusDistribution
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: Object.entries(dist).map(([status, count]) => ({
        name: statusLabels[Number(status)] ?? `状态${status}`,
        value: count
      }))
    }]
  }
})

const deptChartOption = computed(() => {
  if (!dashboard.value) return {}
  const dist = dashboard.value.departmentDistribution
  const entries = Object.entries(dist).sort((a, b) => b[1] - a[1]).slice(0, 8)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: entries.map(e => e[0]), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: entries.map(e => e[1]), itemStyle: { color: '#409eff' } }]
  }
})

const trendChartOption = computed(() => {
  if (!dashboard.value) return {}
  const trend = dashboard.value.weeklyTrend
  const revenue = dashboard.value.weeklyRevenue
  const dates = Object.keys(trend)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['挂号量', '收入'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '挂号量' },
      { type: 'value', name: '收入(元)' }
    ],
    series: [
      { name: '挂号量', type: 'line', data: Object.values(trend), smooth: true },
      { name: '收入', type: 'bar', yAxisIndex: 1, data: Object.values(revenue), itemStyle: { color: '#67c23a' } }
    ]
  }
})

onMounted(reload)
</script>

<template>
  <div class="dashboard-page">
    <PageHeader title="数据看板" subtitle="订单统计、挂号趋势与科室分布" />

    <ErrorState v-if="error" :message="error" @retry="reload" />

    <template v-else>
      <div class="stat-cards" :aria-busy="loading">
        <ElSkeleton v-if="loading" :rows="2" animated />
        <template v-else-if="dashboard">
          <div class="stat-card">
            <span class="stat-label">今日挂号</span>
            <strong class="stat-value">{{ dashboard.todayOrderCount }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-label">今日收入</span>
            <strong class="stat-value">¥{{ dashboard.todayRevenue.toFixed(2) }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-label">就诊人总数</span>
            <strong class="stat-value">{{ dashboard.totalPatients }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-label">医生 / 科室</span>
            <strong class="stat-value">{{ dashboard.totalDoctors }} / {{ dashboard.totalDepartments }}</strong>
          </div>
        </template>
      </div>

      <div class="charts-grid" :aria-busy="loading">
        <ElSkeleton v-if="loading" :rows="6" animated />
        <template v-else-if="dashboard">
          <section class="chart-card">
            <h3>订单状态分布</h3>
            <VChart :option="statusChartOption" autoresize style="height: 300px" />
          </section>
          <section class="chart-card">
            <h3>科室挂号量 TOP8</h3>
            <VChart :option="deptChartOption" autoresize style="height: 300px" />
          </section>
          <section class="chart-card chart-card-wide">
            <h3>最近 7 天趋势</h3>
            <VChart :option="trendChartOption" autoresize style="height: 320px" />
          </section>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard-page { width: min(100%, 1200px); margin: 0 auto; padding: 24px 16px 32px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.stat-card { padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.stat-label { display: block; color: var(--color-muted); font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; color: var(--color-brand-deep); }
.charts-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
.chart-card { padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.chart-card h3 { margin: 0 0 16px; font-size: 16px; color: var(--color-brand-deep); }
.chart-card-wide { grid-column: 1 / -1; }
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .charts-grid { grid-template-columns: 1fr; }
}
</style>
