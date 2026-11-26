<script setup lang="ts">
import { computed } from 'vue'
import { ElTag } from 'element-plus'

const props = defineProps<{
  type: 'success' | 'warning' | 'danger' | 'info'
  text: string
}>()

const color = computed(() => ({
  success: '#087f7a',
  warning: '#b7791f',
  danger: '#c53030',
  info: '#526d7a'
}[props.type]))
</script>

<template>
  <ElTag :color="color" effect="plain" style="color: var(--color-text); border: 0">
    {{ text }}
  </ElTag>
</template>
