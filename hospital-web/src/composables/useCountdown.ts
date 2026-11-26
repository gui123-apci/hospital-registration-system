import { computed, onBeforeUnmount, ref, type Ref } from 'vue'

export function useCountdown(deadline: Ref<string | undefined>) {
  const now = ref(Date.now())
  const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  onBeforeUnmount(() => window.clearInterval(timer))

  const text = computed(() => {
    if (!deadline.value) return ''
    const remaining = new Date(deadline.value).getTime() - now.value
    if (!Number.isFinite(remaining)) return ''
    const seconds = Math.max(0, Math.ceil(remaining / 1000))
    const mm = String(Math.floor(seconds / 60)).padStart(2, '0')
    const ss = String(seconds % 60).padStart(2, '0')
    return `${mm}:${ss}`
  })

  return { text }
}
