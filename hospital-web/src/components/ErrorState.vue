<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <ElAlert type="error" :title="message" show-icon />
  <ElButton class="error-retry" @click="$emit('retry')">重试</ElButton>
</template>
