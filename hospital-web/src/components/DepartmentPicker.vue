<script setup lang="ts">
import NameIdSelect from '@/components/NameIdSelect.vue'
defineProps<{ modelValue?: number; options: { label: string; value: number }[]; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
</script>

<template>
  <NameIdSelect
    :model-value="modelValue"
    :options="options"
    :disabled="disabled"
    placeholder="请选择科室"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
