import { ref, shallowRef } from 'vue'

export function useAsync<T>(loader: () => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref('')

  let requestId = 0

  async function reload() {
    const currentRequest = ++requestId
    loading.value = true
    error.value = ''
    try {
      const result = await loader()
      if (currentRequest !== requestId) return
      data.value = result
    } catch (reason) {
      if (currentRequest !== requestId) return
      error.value = reason instanceof Error ? reason.message : '加载失败'
    } finally {
      if (currentRequest === requestId) loading.value = false
    }
  }

  return { data, loading, error, reload }
}
