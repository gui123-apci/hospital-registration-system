<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import NotificationBell from '@/components/NotificationBell.vue'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="patient-layout">
    <header class="patient-header">
      <div class="patient-brand">
        <span class="patient-logo" aria-hidden="true">H</span>
        <div>
          <strong>智慧医院</strong>
          <span>患者服务</span>
        </div>
      </div>
      <div class="patient-account">
        <NotificationBell />
        <span class="patient-avatar" aria-hidden="true">{{ auth.user?.username?.slice(0, 1) || '患' }}</span>
        <span class="patient-name">{{ auth.user?.username || '患者' }}</span>
        <button class="logout-button" type="button" @click="logout">退出</button>
      </div>
    </header>

    <main class="patient-content">
      <RouterView />
    </main>

    <nav class="patient-tabs" aria-label="患者主导航">
      <RouterLink to="/patient/home" class="patient-tab" active-class="is-active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10.5 12 3l9 7.5M5 9.5V21h14V9.5M9 21v-7h6v7" /></svg>
        <span>首页</span>
      </RouterLink>
      <RouterLink to="/patient/departments" class="patient-tab" active-class="is-active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3h8v4h4v14H4V7h4V3Zm0 4h8M8 12h8M8 16h5" /></svg>
        <span>挂号</span>
      </RouterLink>
      <RouterLink to="/patient/orders" class="patient-tab" active-class="is-active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6M9 12h6" /></svg>
        <span>订单</span>
      </RouterLink>
      <RouterLink to="/patient/patients" class="patient-tab" active-class="is-active">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8" /></svg>
        <span>我的</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.patient-layout {
  min-width: 360px;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--color-page);
}

.patient-header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px max(16px, env(safe-area-inset-left)) 12px max(16px, env(safe-area-inset-right));
  border-bottom: 1px solid rgba(220, 231, 235, 0.9);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
}

.patient-brand,
.patient-account { display: flex; align-items: center; }
.patient-brand { gap: 10px; min-width: 0; }
.patient-brand div { display: grid; gap: 1px; }
.patient-brand strong { color: var(--color-brand-deep); font-size: 16px; }
.patient-brand div span { color: var(--color-muted); font-size: 11px; }

.patient-logo {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
  background: var(--color-brand);
  color: white;
  font-weight: 800;
}

.patient-account { gap: 8px; min-width: 0; }
.patient-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand-deep);
  font-size: 12px;
  font-weight: 700;
}
.patient-name { max-width: 92px; overflow: hidden; color: var(--color-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.logout-button { border: 0; background: transparent; color: var(--color-brand); cursor: pointer; font-size: 12px; padding: 6px 2px; }

.patient-content { min-height: calc(100vh - 68px); min-height: calc(100dvh - 68px); padding-bottom: calc(76px + env(safe-area-inset-bottom)); }

.patient-tabs {
  position: fixed;
  z-index: 30;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  min-height: 66px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -8px 30px rgba(18, 57, 74, 0.05);
}

.patient-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  border-radius: 12px;
  color: var(--color-muted);
  font-size: 11px;
  text-decoration: none;
}
.patient-tab svg { width: 21px; height: 21px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
.patient-tab.is-active { color: var(--color-brand); background: var(--color-brand-soft); font-weight: 700; }
.patient-tab.is-disabled { opacity: 0.5; }

@media (min-width: 760px) {
  .patient-layout { width: min(100%, 720px); margin: 0 auto; background: white; box-shadow: 0 0 60px rgba(18, 57, 74, 0.06); }
  .patient-tabs { right: auto; left: 50%; width: min(100%, 720px); transform: translateX(-50%); }
}
</style>
