<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const router = useRouter()
</script>

<template>
  <main style="padding: 48px; text-align: center">
    <h1>403</h1>
    <p>当前账号无权访问此页面</p>
    <button type="button" @click="router.push(auth.homePath)">返回角色首页</button>
  </main>
</template>
