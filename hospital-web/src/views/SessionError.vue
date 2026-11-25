<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { sessionFailureDestination } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const retrying = ref(false)
const errorMessage = ref('')

const retryPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : auth.homePath
})

async function retry() {
  if (retrying.value) return
  retrying.value = true
  errorMessage.value = ''
  try {
    await auth.restore()
    await router.replace(retryPath.value)
  } catch (error) {
    const destination = sessionFailureDestination(error)
    if (destination === '/session-error') {
      errorMessage.value = error instanceof Error ? error.message : '会话恢复失败，请稍后重试'
    } else {
      await router.replace(destination)
    }
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <main class="session-error">
    <section aria-labelledby="session-error-title">
      <span class="session-error-mark" aria-hidden="true">!</span>
      <h1 id="session-error-title">会话恢复失败</h1>
      <p>网络或服务暂时不可用，登录状态已保留。</p>
      <p v-if="errorMessage" class="session-error-message" role="alert">{{ errorMessage }}</p>
      <div class="session-error-actions">
        <button type="button" :disabled="retrying" @click="retry">
          {{ retrying ? '重试中…' : '重试' }}
        </button>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.session-error {
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  place-items: center;
  padding: 32px 20px;
  background: var(--color-page);
  text-align: center;
}
.session-error section { width: min(100%, 420px); }
.session-error-mark {
  display: grid;
  width: 52px;
  height: 52px;
  margin: 0 auto 20px;
  place-items: center;
  border-radius: 16px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 26px;
  font-weight: 800;
}
.session-error h1 { margin: 0 0 12px; color: var(--color-brand-deep); font-size: 26px; }
.session-error p { margin: 8px 0; color: var(--color-muted); line-height: 1.7; }
.session-error .session-error-message { color: #a33a32; }
.session-error-actions { display: flex; align-items: center; justify-content: center; gap: 20px; margin-top: 28px; }
.session-error button {
  padding: 10px 24px;
  border: 0;
  border-radius: 10px;
  background: var(--color-brand);
  color: white;
  cursor: pointer;
}
.session-error button:disabled { cursor: wait; opacity: .65; }
.session-error a { color: var(--color-brand); text-decoration: none; }
.session-error a:hover { text-decoration: underline; }
</style>
