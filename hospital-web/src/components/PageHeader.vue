<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-header-actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}
.page-header > div:first-child { min-width: 0; }
.page-header h1 {
  margin: 0;
  color: var(--color-brand-deep);
  font-size: 24px;
  line-height: 1.35;
  letter-spacing: -.02em;
  overflow-wrap: anywhere;
}
.page-header p {
  margin: 7px 0 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.7;
}
.page-header-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
@media (max-width: 560px) {
  .page-header { gap: 12px; }
  .page-header h1 { font-size: 22px; }
  .page-header p { font-size: 13px; }
}
</style>
