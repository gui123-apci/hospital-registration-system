<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElMessage, ElRadioButton, ElRadioGroup, ElSkeleton } from 'element-plus'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAsync } from '@/composables/useAsync'
import { canPay, canRefund, canSign } from '@/utils/orderStatus'
import { timeSlotLabel } from '@/utils/schedule'
import { serverNow } from '@/utils/serverClock'
import type { Order, OrderStatus } from '@/types/models'

const PAGE_SIZE = 10
const page = ref(1)
// '' 表示不筛选：后端 status 是可选参数，空值不应作为查询条件发出去
const statusFilter = ref<OrderStatus | ''>('')
const statusOptions: { label: string; value: OrderStatus | '' }[] = [
  { label: '全部', value: '' },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '就诊中', value: 6 },
  { label: '已退号', value: 2 },
  { label: '已就诊', value: 3 },
  { label: '已取消', value: 4 },
  { label: '爽约', value: 5 }
]
const now = ref(serverNow())
const refundTarget = ref<Order | null>(null)
const submitting = ref(false)
const actionError = ref('')
const actionSuccess = ref('')
let clockTimer: number | undefined

const { data, loading, error, reload } = useAsync(() => orderApi.list(
  page.value,
  PAGE_SIZE,
  statusFilter.value === '' ? undefined : statusFilter.value
))
const orders = computed(() => data.value?.records ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const refundMessage = computed(() => refundTarget.value
  ? `确认退号订单 ${refundTarget.value.orderNo} 吗？退号后如需就诊请重新挂号。`
  : '')
const refundVisible = computed({
  get: () => refundTarget.value !== null,
  set: (value: boolean) => {
    if (!value) refundTarget.value = null
  }
})

function availableActions(order: Order) {
  return {
    pay: canPay(order, now.value),
    refund: canRefund(order, now.value),
    sign: canSign(order, now.value)
  }
}

function formatDeadline(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function goToPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
}

async function reloadAfterAction() {
  await reload()
  if (page.value > totalPages.value) page.value = totalPages.value
}

async function refreshAfterMutation() {
  try {
    await reloadAfterAction()
    if (error.value) actionError.value = `订单操作成功，但列表刷新失败：${error.value}`
  } catch (reason) {
    const message = reason instanceof Error ? reason.message : '未知错误'
    actionError.value = `订单操作成功，但列表刷新失败：${message}`
  }
}

function retryLoad() {
  actionError.value = ''
  void reload()
}

async function refundOrder() {
  const target = refundTarget.value
  if (!target || submitting.value) return
  refundTarget.value = null
  if (!canRefund(target, serverNow())) {
    actionError.value = '已超过退号时间，无法退号'
    return
  }
  submitting.value = true
  actionError.value = ''
  actionSuccess.value = ''
  try {
    try {
      await paymentApi.refund(target.id)
    } catch (reason) {
      actionError.value = reason instanceof Error ? reason.message : '退号失败，请稍后重试'
      return
    }
    actionSuccess.value = '退号成功'
    ElMessage.success('退号成功')
    await refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

async function signOrder(order: Order) {
  if (submitting.value) return
  if (!canSign(order, serverNow())) {
    actionError.value = '当前不在签到时间内，无法签到'
    return
  }
  submitting.value = true
  actionError.value = ''
  actionSuccess.value = ''
  try {
    try {
      await orderApi.sign(order.id)
    } catch (reason) {
      actionError.value = reason instanceof Error ? reason.message : '签到失败，请稍后重试'
      return
    }
    actionSuccess.value = '签到成功'
    ElMessage.success('签到成功')
    await refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

watch(page, () => {
  actionError.value = ''
  actionSuccess.value = ''
  void reload()
})

watch(statusFilter, () => {
  actionError.value = ''
  actionSuccess.value = ''
  // 换筛选条件必须回到第一页：停留在旧页码可能超出新结果集的总页数。
  // page 变化会自带一次 reload，只有页码没变（本就在第 1 页）时才需要手动触发，避免重复请求
  if (page.value === 1) {
    void reload()
  } else {
    page.value = 1
  }
})

onMounted(() => {
  now.value = serverNow()
  clockTimer = window.setInterval(() => { now.value = serverNow() }, 1000)
  void reload()
})

onBeforeUnmount(() => {
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
})
</script>

<template>
  <div class="orders-page">
    <PageHeader title="我的订单" subtitle="查看挂号状态，及时完成支付、退号与签到">
      <RouterLink to="/patient/departments" class="back-link">预约挂号</RouterLink>
    </PageHeader>

    <ElAlert
      v-if="actionSuccess"
      class="action-alert"
      type="success"
      :title="actionSuccess"
      show-icon
      :closable="false"
    />
    <ElAlert
      v-if="actionError"
      class="action-alert"
      type="error"
      :title="actionError"
      show-icon
      :closable="false"
    />

    <div class="status-filter">
      <ElRadioGroup v-model="statusFilter" aria-label="按订单状态筛选">
        <ElRadioButton v-for="option in statusOptions" :key="option.label" :value="option.value">
          {{ option.label }}
        </ElRadioButton>
      </ElRadioGroup>
    </div>

    <section class="orders-panel" aria-label="订单列表" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载订单">
        <ElSkeleton v-for="item in 3" :key="item" :rows="4" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="retryLoad" />
      <div v-else-if="!orders.length" class="empty-state">
        <EmptyState :description="statusFilter === '' ? '暂无订单，预约挂号后可在这里查看' : '该状态下暂无订单'" />
        <RouterLink v-if="statusFilter === ''" to="/patient/departments" class="primary-link">去挂号</RouterLink>
      </div>
      <div v-else class="order-list">
        <article v-for="order in orders" :key="order.id" class="order-card" :aria-label="`订单 ${order.orderNo}`">
          <div class="order-heading">
            <div>
              <span class="order-label">订单号</span>
              <strong class="order-number">{{ order.orderNo }}</strong>
            </div>
            <OrderStatusTag :status="order.status" />
          </div>

          <dl class="order-details">
            <div><dt>就诊人</dt><dd>{{ order.patientName }}</dd></div>
            <div><dt>就诊日期</dt><dd>{{ order.workDate }}</dd></div>
            <div><dt>出诊时段</dt><dd>{{ timeSlotLabel(order.timeSlot) }}</dd></div>
            <div><dt>挂号金额</dt><dd class="amount">¥{{ order.amount.toFixed(2) }}</dd></div>
            <div><dt>支付截止</dt><dd>{{ formatDeadline(order.deadlineTime) }}</dd></div>
          </dl>

          <div v-if="!submitting && (availableActions(order).pay || availableActions(order).refund || availableActions(order).sign)" class="order-actions">
            <RouterLink
              v-if="availableActions(order).pay"
              :to="{ path: '/patient/payment', query: { orderId: order.id } }"
              class="action-button pay-action"
              :aria-label="`支付订单 ${order.orderNo}`"
            >去支付</RouterLink>
            <ElButton
              v-if="availableActions(order).refund"
              class="action-button refund-action"
              size="default"
              @click="refundTarget = order"
            >退号</ElButton>
            <ElButton
              v-if="availableActions(order).sign"
              class="action-button sign-action"
              type="primary"
              size="default"
              @click="signOrder(order)"
            >签到</ElButton>
          </div>
        </article>
      </div>
    </section>

    <nav v-if="total > PAGE_SIZE" class="pagination" aria-label="订单分页">
      <button type="button" :disabled="page <= 1 || loading" @click="goToPage(page - 1)">上一页</button>
      <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button type="button" :disabled="page >= totalPages || loading" @click="goToPage(page + 1)">下一页</button>
    </nav>

    <ConfirmDialog v-model="refundVisible" :message="refundMessage" title="确认退号" @confirm="refundOrder" />
  </div>
</template>

<style scoped>
.orders-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.orders-page :deep(.page-header) { margin-bottom: 20px; }
.orders-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.action-alert { margin-bottom: 14px; }
.status-filter { margin-bottom: 16px; overflow-x: auto; }
.status-filter :deep(.el-radio-group) { flex-wrap: nowrap; }
.orders-panel { min-height: 180px; }
.loading-list { display: grid; gap: 18px; padding: 20px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.order-list { display: grid; gap: 14px; }
.order-card { padding: 18px; border: 1px solid var(--color-border); border-radius: 16px; background: white; box-shadow: 0 8px 24px rgba(18, 57, 74, .04); }
.order-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid #edf2f4; }
.order-heading > div { display: grid; min-width: 0; gap: 4px; }
.order-label { color: var(--color-muted); font-size: 12px; }
.order-number { overflow-wrap: anywhere; color: var(--color-brand-deep); font-size: 15px; }
.order-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; margin: 16px 0 0; }
.order-details > div { display: grid; gap: 5px; min-width: 0; }
.order-details dt { color: var(--color-muted); font-size: 12px; }
.order-details dd { margin: 0; overflow-wrap: anywhere; font-size: 14px; font-weight: 600; line-height: 1.5; }
.order-details .amount { color: #b05b36; font-size: 18px; }
.order-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; padding-top: 14px; border-top: 1px solid #edf2f4; }
.order-actions :deep(.action-button.el-button) { min-height: 44px; margin-left: 0; padding: 10px 20px; border-radius: 10px; font-weight: 700; }
.action-button { display: inline-flex; min-height: 44px; align-items: center; justify-content: center; padding: 10px 20px; border: 1px solid var(--color-border); border-radius: 10px; background: white; color: var(--color-text); font-size: 14px; font-weight: 700; text-decoration: none; cursor: pointer; }
.pay-action { border-color: var(--color-brand); background: var(--color-brand); color: white; }
.refund-action { color: #a34c3d; }
.sign-action { border-color: var(--color-brand); color: var(--color-brand); }
.empty-state { display: grid; justify-items: center; gap: 4px; padding: 22px 12px 30px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.primary-link { display: inline-flex; min-height: 44px; align-items: center; justify-content: center; padding: 10px 24px; border-radius: 10px; background: var(--color-brand); color: white; font-weight: 700; text-decoration: none; }
.pagination { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; margin-top: 18px; }
.pagination button { min-height: 44px; padding: 8px 13px; border: 1px solid var(--color-border); border-radius: 10px; background: white; color: var(--color-brand); cursor: pointer; }
.pagination button:disabled { color: #9aadb5; background: #f5f8f9; cursor: not-allowed; }
.pagination span { color: var(--color-muted); font-size: 12px; text-align: center; }
@media (max-width: 560px) {
  .orders-page { padding: 20px 14px 28px; }
  .orders-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .order-card { padding: 16px 14px; }
  .order-actions { justify-content: stretch; }
  .order-actions .action-button { flex: 1; }
  .pagination { gap: 7px; }
  .pagination button { padding: 8px 10px; }
}
</style>
