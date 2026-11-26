<script setup lang="ts">
import { computed } from 'vue'
import { ElDrawer } from 'element-plus'
const props = withDefaults(defineProps<{ modelValue: boolean; title: string; size?: string }>(), {
  size: '440px'
})
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
</script>

<template>
  <ElDrawer v-model="visible" :title="title" :size="size"><slot /></ElDrawer>
</template>
