<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { orderApi } from '@/api/order'
import { paymentApi } from '@/api/payment'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PaymentCountdown from '@/components/PaymentCountdown.vue'
import { useAsync } from '@/composables/useAsync'
import { canPay } from '@/utils/orderStatus'
import { timeSlotLabel } from '@/utils/schedule'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const paid = ref(false)
const paymentExpired = ref(false)
const submitError = ref('')
const navigationError = ref('')
let contextVersion = 0

function readOrderId(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

const { data: order, loading, error, reload } = useAsync(async () => {
  const orderId = readOrderId(route.query.orderId)
  if (orderId === null) throw new Error('订单编号无效，请返回订单列表')
  const matched = await orderApi.get(orderId)
  if (matched.status !== 0) throw new Error('该订单当前不可支付，请返回订单列表')
  return matched
})

const payable = computed(() => Boolean(order.value)
  && !paid.value
  && !paymentExpired.value
  && canPay(order.value!, new Date()))
const canSubmit = computed(() => payable.value && !loading.value && !submitting.value)

async function pay() {
  if (!canSubmit.value || !order.value) return
  const paymentContext = contextVersion
  const target = order.value
  submitting.value = true
  submitError.value = ''
  navigationError.value = ''
  try {
    await paymentApi.pay(target.id, 0)
  } catch (reason) {
    if (paymentContext === contextVersion) {
      submitError.value = reason instanceof Error ? reason.message : '支付失败，请稍后重试'
    }
    return
  } finally {
    if (paymentContext === contextVersion) submitting.value = false
  }

  if (paymentContext !== contextVersion) return
  paid.value = true
  ElMessage.success('模拟支付成功')
  try {
    await router.push('/patient/orders')
  } catch {
    if (paymentContext === contextVersion) {
      navigationError.value = '模拟支付成功，但返回订单列表失败，请使用上方链接手动返回'
    }
  }
}

watch(() => route.query.orderId, () => {
  contextVersion += 1
  paid.value = false
  submitting.value = false
  paymentExpired.value = false
  submitError.value = ''
  navigationError.value = ''
  void reload()
})

onMounted(reload)
</script>

<template>
  <div class="payment-page">
    <PageHeader title="模拟支付" subtitle="当前为支付流程演示，不会产生真实扣款">
      <RouterLink to="/patient/orders" class="back-link">返回订单列表</RouterLink>
    </PageHeader>

    <div v-if="loading" class="loading-card" role="status" aria-label="正在加载支付订单">
      <ElSkeleton :rows="5" animated />
    </div>
    <ErrorState v-else-if="error" :message="error" @retry="reload" />

    <section v-else-if="order" class="payment-card" aria-labelledby="payment-title">
      <div class="simulation-label">模拟支付</div>
      <h2 id="payment-title">确认支付</h2>
      <p class="payment-amount">¥{{ order.amount.toFixed(2) }}</p>

      <dl class="payment-details">
        <div><dt>订单号</dt><dd>{{ order.orderNo }}</dd></div>
        <div><dt>就诊人</dt><dd>{{ order.patientName }}</dd></div>
        <div><dt>就诊时间</dt><dd>{{ order.workDate }} {{ timeSlotLabel(order.timeSlot) }}</dd></div>
      </dl>

      <div class="countdown-row">
        <span>支付剩余时间</span>
        <strong><PaymentCountdown :deadline="order.deadlineTime" @expired="paymentExpired = true" /></strong>
      </div>
      <p v-if="paid" class="paid-hint" role="status">模拟支付已完成，请勿重复支付</p>
      <p v-else-if="!payable" class="expired-hint" role="status">支付已超时，请返回订单列表重新挂号</p>
      <p class="simulation-hint">点击下方按钮完成模拟支付，不会调用真实支付渠道。</p>

      <ElAlert v-if="submitError" class="form-alert" type="error" :title="submitError" show-icon :closable="false" />
      <ElAlert v-if="navigationError" class="form-alert" type="warning" :title="navigationError" show-icon :closable="false" />
      <ElButton
        type="primary"
        size="large"
        class="pay-button"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="pay"
      >{{ paid ? '支付已完成' : submitting ? '正在支付…' : '模拟支付' }}</ElButton>
    </section>

    <EmptyState v-else description="暂无支付订单" />
  </div>
</template>

<style scoped>
.payment-page { width: min(100%, 640px); margin: 0 auto; padding: 24px 16px 32px; }
.payment-page :deep(.page-header) { margin-bottom: 20px; }
.payment-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.back-link { color: var(--color-brand); font-size: 13px; text-decoration: none; white-space: nowrap; }
.loading-card { padding: 24px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.payment-card { display: grid; justify-items: center; padding: 26px 20px 22px; border: 1px solid var(--color-border); border-radius: 20px; background: white; box-shadow: 0 12px 32px rgba(18, 57, 74, .06); text-align: center; }
.simulation-label { padding: 5px 11px; border-radius: 999px; background: #fff4df; color: #94651e; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.payment-card h2 { margin: 15px 0 0; color: var(--color-brand-deep); font-size: 19px; }
.payment-amount { margin: 8px 0 22px; color: #b05b36; font-size: 36px; font-weight: 800; line-height: 1.2; }
.payment-details { display: grid; width: 100%; gap: 0; margin: 0; text-align: left; }
.payment-details > div { display: grid; grid-template-columns: 86px minmax(0, 1fr); gap: 12px; padding: 12px 0; border-top: 1px solid #edf2f4; }
.payment-details dt { color: var(--color-muted); font-size: 13px; }
.payment-details dd { margin: 0; overflow-wrap: anywhere; font-size: 14px; font-weight: 600; line-height: 1.5; }
.countdown-row { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 12px; margin-top: 8px; padding: 14px; border-radius: 11px; background: var(--color-brand-soft); color: var(--color-brand-deep); font-size: 13px; }
.countdown-row strong { color: var(--color-brand); font-size: 20px; font-variant-numeric: tabular-nums; letter-spacing: .04em; }
.expired-hint { margin: 12px 0 0; color: #a34c3d; font-size: 13px; }
.paid-hint { margin: 12px 0 0; color: #28735d; font-size: 13px; font-weight: 600; }
.simulation-hint { margin: 16px 0; color: var(--color-muted); font-size: 12px; line-height: 1.7; }
.form-alert { width: 100%; margin-bottom: 14px; text-align: left; }
.pay-button { width: 100%; min-height: 50px; border-radius: 11px; font-size: 16px; font-weight: 700; }
@media (max-width: 560px) {
  .payment-page { padding: 20px 14px 28px; }
  .payment-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .payment-card { padding: 23px 16px 18px; }
  .payment-details > div { grid-template-columns: 78px minmax(0, 1fr); }
}
</style>
