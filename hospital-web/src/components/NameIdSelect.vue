<script setup lang="ts">
import { ElSelect, ElOption } from 'element-plus'

export interface NameIdOption {
  label: string
  value: number
}

defineProps<{
  modelValue?: number
  options: NameIdOption[]
  placeholder?: string
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()
</script>

<template>
  <ElSelect
    :model-value="modelValue"
    :placeholder="placeholder ?? '请选择'"
    :disabled="disabled"
    clearable
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <ElOption
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </ElSelect>
</template>
