<script setup lang="ts">
import { ElEmpty } from 'element-plus'
defineProps<{ description?: string }>()
</script>

<template>
  <ElEmpty :description="description ?? '暂无数据'" />
</template>
