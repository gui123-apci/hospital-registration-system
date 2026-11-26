<script setup lang="ts">
import { ElTable } from 'element-plus'
defineProps<{ loading?: boolean; emptyText?: string }>()
</script>

<template>
  <ElTable v-loading="loading" :empty-text="emptyText ?? '暂无数据'" stripe>
    <slot name="default" />
  </ElTable>
</template>
