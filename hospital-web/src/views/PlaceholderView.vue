<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <main style="padding: 48px; min-height: 60vh">
    <h1>{{ String(route.name ?? '页面') }}</h1>
    <p>页面将在后续任务中实现。</p>
  </main>
</template>
