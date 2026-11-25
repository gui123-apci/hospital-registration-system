<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <main class="auth-layout">
    <div class="auth-glow auth-glow-one" aria-hidden="true" />
    <div class="auth-glow auth-glow-two" aria-hidden="true" />
    <section class="auth-shell">
      <header class="auth-brand">
        <span class="brand-mark" aria-hidden="true">H</span>
        <div>
          <strong>智慧医院</strong>
          <span>让就医更简单</span>
        </div>
      </header>
      <div class="auth-card">
        <RouterView />
      </div>
      <footer class="auth-footer">安全连接 · 便捷就诊 · 全程守护</footer>
    </section>
  </main>
</template>

<style scoped>
.auth-layout {
  position: relative;
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  place-items: center;
  overflow: hidden;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 14% 18%, rgba(8, 127, 122, 0.16), transparent 34%),
    linear-gradient(135deg, #eef8f6 0%, #f7fafb 48%, #e8f0f5 100%);
}

.auth-glow {
  position: absolute;
  border: 1px solid rgba(8, 127, 122, 0.12);
  border-radius: 50%;
  pointer-events: none;
}

.auth-glow-one { width: 460px; height: 460px; top: -240px; right: -130px; }
.auth-glow-two { width: 320px; height: 320px; bottom: -190px; left: -110px; }

.auth-shell {
  position: relative;
  z-index: 1;
  width: min(100%, 460px);
}

.auth-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 24px;
  color: var(--color-brand-deep);
}

.brand-mark {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 14px;
  background: var(--color-brand);
  color: white;
  font-size: 24px;
  font-weight: 800;
  box-shadow: 0 10px 24px rgba(8, 127, 122, 0.22);
}

.auth-brand div { display: grid; gap: 2px; }
.auth-brand strong { font-size: 22px; letter-spacing: 0.08em; }
.auth-brand div span { color: var(--color-muted); font-size: 12px; letter-spacing: 0.16em; }

.auth-card {
  overflow: hidden;
  border: 1px solid rgba(220, 231, 235, 0.9);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 70px rgba(18, 57, 74, 0.12);
}

.auth-footer {
  margin-top: 20px;
  color: var(--color-muted);
  font-size: 12px;
  text-align: center;
  letter-spacing: 0.08em;
}

@media (max-width: 480px) {
  .auth-layout { padding: 24px 16px; }
  .auth-card { border-radius: 20px; }
}
</style>
