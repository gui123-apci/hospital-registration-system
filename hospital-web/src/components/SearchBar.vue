<script setup lang="ts">
import { computed } from 'vue'
import { ElInput, ElButton } from 'element-plus'
const props = defineProps<{ modelValue: string; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; search: [] }>()
const value = computed({ get: () => props.modelValue, set: (next) => emit('update:modelValue', next) })
</script>

<template>
  <div class="search-bar">
    <ElInput v-model="value" :placeholder="placeholder ?? '请输入关键词'" @keyup.enter="emit('search')" />
    <ElButton @click="emit('search')">查询</ElButton>
  </div>
</template>
