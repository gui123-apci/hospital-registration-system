<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="doctor-layout">
    <aside class="doctor-sidebar">
      <div class="doctor-brand">
        <span class="doctor-logo" aria-hidden="true">H</span>
        <div><strong>智慧医院</strong><span>医生工作空间</span></div>
      </div>
      <nav class="doctor-nav" aria-label="医生导航">
        <RouterLink to="/doctor/workbench" class="doctor-nav-item is-active">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z" /></svg>
          <span>工作台</span>
        </RouterLink>
        <span class="doctor-nav-item is-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18M8 14h3M8 17h6" /></svg>
          <span>排班</span>
        </span>
        <span class="doctor-nav-item is-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h9l4 4v14H6zM14 3v5h5M9 12h7M9 16h5" /></svg>
          <span>报告</span>
        </span>
        <span class="doctor-nav-item is-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8" /></svg>
          <span>我的</span>
        </span>
      </nav>
      <div class="doctor-sidebar-note"><span class="status-dot" />诊疗服务在线</div>
    </aside>

    <div class="doctor-shell">
      <header class="doctor-topbar">
        <div>
          <p>DOCTOR CONSOLE</p>
          <h1>今日工作台</h1>
        </div>
        <div class="doctor-user">
          <span class="doctor-avatar" aria-hidden="true">{{ auth.user?.username?.slice(0, 1) || '医' }}</span>
          <div><strong>{{ auth.user?.username || '医生' }}</strong><span>医生账号</span></div>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </header>
      <main class="doctor-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.doctor-layout { display: grid; grid-template-columns: 236px minmax(0, 1fr); min-height: 100vh; min-height: 100dvh; background: #edf3f5; }
.doctor-sidebar { position: sticky; top: 0; display: flex; height: 100vh; height: 100dvh; flex-direction: column; padding: 28px 18px 22px; background: linear-gradient(180deg, #12394a 0%, #0b2835 100%); color: #dce9ed; }
.doctor-brand { display: flex; align-items: center; gap: 11px; padding: 0 10px 28px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.doctor-logo { display: grid; width: 40px; height: 40px; flex: 0 0 auto; place-items: center; border: 1px solid rgba(255, 255, 255, 0.18); border-radius: 12px; background: rgba(8, 127, 122, 0.8); color: white; font-weight: 800; }
.doctor-brand div { display: grid; gap: 4px; }
.doctor-brand strong { color: white; font-size: 17px; letter-spacing: 0.05em; }
.doctor-brand div span { color: #8fa9b3; font-size: 11px; }
.doctor-nav { display: grid; gap: 8px; margin-top: 28px; }
.doctor-nav-item { display: flex; min-height: 48px; align-items: center; gap: 13px; padding: 0 14px; border: 1px solid transparent; border-radius: 12px; color: #9fb7c0; font-size: 14px; text-decoration: none; }
.doctor-nav-item svg { width: 20px; height: 20px; flex: 0 0 auto; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
.doctor-nav-item.is-active { border-color: rgba(88, 211, 195, 0.15); background: rgba(8, 127, 122, 0.28); color: #eafffb; box-shadow: inset 3px 0 0 #58d3c3; }
.doctor-nav-item.is-disabled { opacity: 0.5; }
.doctor-sidebar-note { display: flex; align-items: center; gap: 8px; margin-top: auto; padding: 14px 12px 0; border-top: 1px solid rgba(255, 255, 255, 0.08); color: #8fa9b3; font-size: 12px; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #58d3c3; box-shadow: 0 0 0 4px rgba(88, 211, 195, 0.1); }
.doctor-shell { min-width: 0; }
.doctor-topbar { position: sticky; z-index: 10; top: 0; display: flex; min-height: 88px; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 32px; border-bottom: 1px solid var(--color-border); background: rgba(255, 255, 255, 0.94); backdrop-filter: blur(12px); }
.doctor-topbar p { margin: 0 0 5px; color: var(--color-brand); font-size: 10px; font-weight: 700; letter-spacing: 0.16em; }
.doctor-topbar h1 { margin: 0; color: var(--color-brand-deep); font-size: 22px; }
.doctor-user { display: flex; align-items: center; gap: 10px; }
.doctor-avatar { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 12px; background: var(--color-brand-soft); color: var(--color-brand); font-weight: 800; }
.doctor-user div { display: grid; gap: 3px; min-width: 0; }
.doctor-user strong { max-width: 150px; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.doctor-user div span { color: var(--color-muted); font-size: 11px; }
.doctor-user button { margin-left: 8px; padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 9px; background: white; color: var(--color-muted); cursor: pointer; font-size: 12px; }
.doctor-user button:hover { border-color: #b6d9d4; color: var(--color-brand); }
.doctor-content { min-width: 0; padding: 28px 32px 48px; }

@media (max-width: 980px) {
  .doctor-layout { grid-template-columns: 84px minmax(0, 1fr); }
  .doctor-sidebar { align-items: center; padding: 24px 12px; }
  .doctor-brand { justify-content: center; padding: 0 0 24px; }
  .doctor-brand div,
  .doctor-nav-item span,
  .doctor-sidebar-note { display: none; }
  .doctor-nav { width: 100%; }
  .doctor-nav-item { justify-content: center; padding: 0; }
  .doctor-topbar { padding: 18px 22px; }
  .doctor-content { padding: 22px; }
}

@media (max-width: 680px) {
  .doctor-layout { display: block; }
  .doctor-sidebar { position: static; display: block; width: 100%; height: auto; padding: 14px 16px; }
  .doctor-brand { justify-content: flex-start; padding: 0 0 12px; }
  .doctor-brand div { display: grid; }
  .doctor-nav { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin-top: 12px; }
  .doctor-nav-item { min-height: 42px; gap: 6px; font-size: 12px; }
  .doctor-nav-item span { display: inline; }
  .doctor-nav-item.is-active { box-shadow: inset 0 -2px 0 #58d3c3; }
  .doctor-topbar { position: static; min-height: 76px; padding: 14px 16px; }
  .doctor-topbar h1 { font-size: 18px; }
  .doctor-user div { display: none; }
  .doctor-user button { margin-left: 0; }
  .doctor-content { padding: 16px 16px 32px; }
}
</style>
