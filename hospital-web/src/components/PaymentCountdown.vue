<script setup lang="ts">
import { computed, watch } from 'vue'
import { useCountdown } from '@/composables/useCountdown'

const props = defineProps<{ deadline?: string }>()
const emit = defineEmits<{ expired: [] }>()
const { text } = useCountdown(computed(() => props.deadline))

watch(text, (value, previous) => {
  if (value === '00:00' && previous !== '00:00') emit('expired')
})
</script>

<template>
  <span :aria-label="text ? `支付剩余时间 ${text}` : undefined">{{ text }}</span>
</template>
