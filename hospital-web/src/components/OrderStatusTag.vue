<script setup lang="ts">
import { computed } from 'vue'
import StatusTag from '@/components/StatusTag.vue'
import type { OrderStatus } from '@/types/models'
const props = defineProps<{ status: OrderStatus }>()
const config = computed(() => ({
  0: { text: '待支付', type: 'warning' as const },
  1: { text: '已支付', type: 'success' as const },
  2: { text: '已退号', type: 'info' as const },
  3: { text: '已就诊', type: 'success' as const },
  4: { text: '已取消', type: 'info' as const },
  5: { text: '爽约', type: 'danger' as const }
}[props.status]))
</script>

<template>
  <StatusTag :text="config.text" :type="config.type" />
</template>
