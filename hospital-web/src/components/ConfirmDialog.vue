<script setup lang="ts">
import { computed } from 'vue'
import { ElDialog, ElButton } from 'element-plus'
const props = defineProps<{ modelValue: boolean; title?: string; message: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [] }>()
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
</script>

<template>
  <ElDialog v-model="visible" :title="title ?? '操作确认'" width="420px">
    <p>{{ message }}</p>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="danger" @click="emit('confirm')">确认</ElButton>
    </template>
  </ElDialog>
</template>
