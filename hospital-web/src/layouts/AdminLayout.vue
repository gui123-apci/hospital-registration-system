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
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <span class="admin-logo" aria-hidden="true">H</span>
        <div><strong>智慧医院</strong><span>运营管理平台</span></div>
      </div>
      <nav class="admin-nav" aria-label="管理导航">
        <RouterLink to="/admin/departments" class="admin-nav-item is-active">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 21V7l8-4 8 4v14M2 21h20M8 10h2M14 10h2M8 14h2M14 14h2M9 21v-4h6v4" /></svg>
          <span>科室管理</span>
        </RouterLink>
        <span class="admin-nav-item is-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3" /><path d="M3 20c0-4 2.7-7 6-7s6 3 6 7M16 5h5M18.5 2.5v5M16 14c2.8.4 5 2.8 5 6" /></svg>
          <span>医生管理</span>
        </span>
        <span class="admin-nav-item is-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18M8 14h3M8 17h6" /></svg>
          <span>排班管理</span>
        </span>
      </nav>
      <div class="admin-sidebar-footer">
        <span>HOSPITAL OS</span>
        <strong>管理工作区</strong>
      </div>
    </aside>

    <div class="admin-shell">
      <header class="admin-topbar">
        <div class="admin-heading">
          <span class="admin-heading-mark" aria-hidden="true" />
          <div><p>医院运营中心</p><h1>科室管理</h1></div>
        </div>
        <div class="admin-user">
          <span class="admin-avatar" aria-hidden="true">{{ auth.user?.username?.slice(0, 1) || '管' }}</span>
          <div><strong>{{ auth.user?.username || '管理员' }}</strong><span>系统管理员</span></div>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </header>
      <main class="admin-content">
        <section class="admin-workspace" aria-label="管理表格工作区">
          <RouterView />
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout { display: grid; grid-template-columns: 248px minmax(0, 1fr); grid-template-rows: 76px minmax(0, 1fr); min-height: 100vh; min-height: 100dvh; background: #f1f5f7; }
.admin-sidebar { grid-row: 1 / -1; display: flex; min-height: 100vh; min-height: 100dvh; flex-direction: column; padding: 24px 18px 20px; background: #102f3c; color: #dbe7eb; }
.admin-brand { display: flex; align-items: center; gap: 11px; padding: 0 10px 25px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.admin-logo { display: grid; width: 40px; height: 40px; flex: 0 0 auto; place-items: center; border-radius: 11px; background: #d7eee8; color: #12394a; font-size: 20px; font-weight: 900; }
.admin-brand div { display: grid; gap: 4px; }
.admin-brand strong { color: white; font-size: 17px; letter-spacing: 0.05em; }
.admin-brand div span { color: #91a9b2; font-size: 11px; }
.admin-nav { display: grid; gap: 8px; margin-top: 28px; }
.admin-nav-item { display: flex; min-height: 48px; align-items: center; gap: 13px; padding: 0 14px; border: 1px solid transparent; border-radius: 10px; color: #9db2ba; font-size: 14px; text-decoration: none; }
.admin-nav-item svg { width: 20px; height: 20px; flex: 0 0 auto; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.65; }
.admin-nav-item.is-active { border-color: rgba(118, 202, 189, 0.14); background: #1d4652; color: #e6fffa; box-shadow: inset 3px 0 0 #76cabd; }
.admin-nav-item.is-disabled { opacity: 0.46; }
.admin-sidebar-footer { display: grid; gap: 6px; margin-top: auto; padding: 18px 12px 0; border-top: 1px solid rgba(255, 255, 255, 0.08); }
.admin-sidebar-footer span { color: #6f8b96; font-size: 9px; letter-spacing: 0.18em; }
.admin-sidebar-footer strong { color: #a9bec6; font-size: 12px; font-weight: 500; }
.admin-shell { min-width: 0; }
.admin-topbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 32px; border-bottom: 1px solid var(--color-border); background: white; }
.admin-heading { display: flex; align-items: center; gap: 13px; }
.admin-heading-mark { width: 4px; height: 34px; border-radius: 4px; background: var(--color-brand); }
.admin-heading p { margin: 0 0 3px; color: var(--color-muted); font-size: 11px; }
.admin-heading h1 { margin: 0; color: var(--color-brand-deep); font-size: 20px; }
.admin-user { display: flex; align-items: center; gap: 10px; }
.admin-avatar { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 10px; background: #e8f0f3; color: var(--color-brand-deep); font-weight: 800; }
.admin-user div { display: grid; gap: 3px; min-width: 0; }
.admin-user strong { max-width: 150px; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.admin-user div span { color: var(--color-muted); font-size: 11px; }
.admin-user button { margin-left: 8px; padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 8px; background: white; color: var(--color-muted); cursor: pointer; font-size: 12px; }
.admin-user button:hover { border-color: #b6d9d4; color: var(--color-brand); }
.admin-content { min-width: 0; padding: 28px 32px 44px; overflow-x: auto; }
.admin-workspace { min-width: 640px; min-height: calc(100vh - 148px); min-height: calc(100dvh - 148px); overflow: hidden; border: 1px solid var(--color-border); border-radius: 16px; background: white; box-shadow: 0 10px 30px rgba(18, 57, 74, 0.04); }

@media (max-width: 900px) {
  .admin-layout { display: block; }
  .admin-sidebar { min-height: auto; padding: 16px; }
  .admin-brand { padding: 0 0 14px; }
  .admin-nav { grid-template-columns: repeat(3, 1fr); margin-top: 14px; }
  .admin-nav-item { justify-content: center; min-height: 44px; gap: 7px; padding: 0 8px; font-size: 12px; }
  .admin-nav-item.is-active { box-shadow: inset 0 -2px 0 #76cabd; }
  .admin-sidebar-footer { display: none; }
  .admin-topbar { min-height: 72px; padding: 0 20px; }
  .admin-content { padding: 20px; }
}

@media (max-width: 620px) {
  .admin-sidebar { padding: 14px 12px; }
  .admin-brand div { display: none; }
  .admin-nav-item { flex-direction: column; gap: 4px; min-height: 58px; }
  .admin-topbar { padding: 0 14px; }
  .admin-heading h1 { font-size: 17px; }
  .admin-user div { display: none; }
  .admin-user button { margin-left: 0; padding: 7px 9px; }
  .admin-content { padding: 14px; }
}
</style>
