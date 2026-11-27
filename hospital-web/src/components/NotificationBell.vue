<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { notificationApi, type NotificationItem } from '@/api/notification'

/** 轮询间隔：通知不是即时通讯，30 秒足够，也避免给后端压力 */
const POLL_INTERVAL = 30_000

const unreadCount = ref(0)
const items = ref<NotificationItem[]>([])
const open = ref(false)
const loading = ref(false)
const error = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

async function refreshUnread() {
  try {
    unreadCount.value = await notificationApi.unreadCount()
  } catch {
    // 轮询失败静默处理：下一轮再试，不打扰用户
  }
}

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  await loadList()
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    items.value = await notificationApi.list()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '通知加载失败'
  } finally {
    loading.value = false
  }
}

async function readOne(item: NotificationItem) {
  if (item.readFlag === 1) return
  try {
    await notificationApi.markRead(item.id)
    item.readFlag = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    // 标记失败不阻塞阅读，下次刷新会纠正
  }
}

async function readAll() {
  try {
    await notificationApi.markAllRead()
    items.value.forEach((item) => { item.readFlag = 1 })
    unreadCount.value = 0
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '操作失败'
  }
}

onMounted(() => {
  void refreshUnread()
  timer = setInterval(refreshUnread, POLL_INTERVAL)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="notification-bell">
    <button
      type="button"
      class="bell-button"
      :aria-label="unreadCount > 0 ? `通知，${unreadCount} 条未读` : '通知'"
      :aria-expanded="open"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M18 8a6 6 0 1 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10.3 21a2 2 0 0 0 3.4 0" />
      </svg>
      <span v-if="unreadCount > 0" class="bell-badge">{{ badgeText }}</span>
    </button>

    <div v-if="open" class="notification-panel" role="dialog" aria-label="通知列表">
      <div class="panel-header">
        <strong>通知</strong>
        <button v-if="unreadCount > 0" type="button" class="read-all" @click="readAll">全部已读</button>
      </div>

      <p v-if="loading" class="panel-tip">加载中…</p>
      <p v-else-if="error" class="panel-tip is-error" role="alert">{{ error }}</p>
      <p v-else-if="!items.length" class="panel-tip">暂无通知</p>

      <ul v-else class="notification-list">
        <li
          v-for="item in items"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-unread': item.readFlag === 0 }"
          @click="readOne(item)"
        >
          <span class="item-dot" aria-hidden="true" />
          <div class="item-body">
            <strong class="item-title">{{ item.title }}</strong>
            <p class="item-content">{{ item.content }}</p>
            <time class="item-time">{{ item.createTime?.replace('T', ' ').slice(0, 16) }}</time>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.notification-bell { position: relative; }
.bell-button {
  position: relative;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
}
.bell-button:hover { background: var(--color-brand-soft); color: var(--color-brand); }
.bell-button svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
.bell-badge {
  position: absolute;
  top: 1px;
  right: 0;
  min-width: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: white;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}
.notification-panel {
  position: absolute;
  z-index: 50;
  top: calc(100% + 8px);
  right: 0;
  width: min(320px, calc(100vw - 32px));
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: white;
  box-shadow: 0 16px 40px rgba(18, 57, 74, 0.14);
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
}
.panel-header strong { color: var(--color-brand-deep); font-size: 14px; }
.read-all { border: 0; background: transparent; color: var(--color-brand); cursor: pointer; font-size: 12px; }
.panel-tip { margin: 0; padding: 18px 14px; color: var(--color-muted); font-size: 13px; text-align: center; }
.panel-tip.is-error { color: #b42318; }
.notification-list { margin: 0; padding: 0; list-style: none; }
.notification-item { display: flex; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--color-border); cursor: pointer; }
.notification-item:last-child { border-bottom: 0; }
.notification-item:hover { background: #fbfdfe; }
.item-dot { width: 7px; height: 7px; flex: 0 0 auto; margin-top: 6px; border-radius: 50%; background: transparent; }
.notification-item.is-unread .item-dot { background: var(--color-brand); }
.item-body { display: grid; gap: 3px; min-width: 0; }
.item-title { color: var(--color-text); font-size: 13px; }
.notification-item.is-unread .item-title { color: var(--color-brand-deep); }
.item-content { margin: 0; color: var(--color-muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.item-time { color: #9fb0b8; font-size: 11px; }
</style>
