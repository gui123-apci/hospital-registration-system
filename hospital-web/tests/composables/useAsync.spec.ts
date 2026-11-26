import { describe, expect, it, vi } from 'vitest'
import { useAsync } from '@/composables/useAsync'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('useAsync', () => {
  it('ignores an older success after the latest request has completed', async () => {
    const first = deferred<string>()
    const latest = deferred<string>()
    const state = useAsync(vi.fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(latest.promise))

    const firstRequest = state.reload()
    const latestRequest = state.reload()
    latest.resolve('latest order')
    await latestRequest
    first.resolve('stale order')
    await firstRequest

    expect(state.data.value).toBe('latest order')
    expect(state.error.value).toBe('')
    expect(state.loading.value).toBe(false)
  })

  it('ignores an older failure after the latest request has completed', async () => {
    const first = deferred<string>()
    const latest = deferred<string>()
    const state = useAsync(vi.fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(latest.promise))

    const firstRequest = state.reload()
    const latestRequest = state.reload()
    latest.resolve('latest order')
    await latestRequest
    first.reject(new Error('stale failure'))
    await firstRequest

    expect(state.data.value).toBe('latest order')
    expect(state.error.value).toBe('')
    expect(state.loading.value).toBe(false)
  })

  it('does not let an older completion clear loading while the latest request is pending', async () => {
    const first = deferred<string>()
    const latest = deferred<string>()
    const state = useAsync(vi.fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(latest.promise))

    const firstRequest = state.reload()
    const latestRequest = state.reload()
    first.resolve('stale order')
    await firstRequest

    expect(state.data.value).toBeNull()
    expect(state.loading.value).toBe(true)

    latest.resolve('latest order')
    await latestRequest
    expect(state.data.value).toBe('latest order')
    expect(state.loading.value).toBe(false)
  })

  it('does not let an older success clear the latest request error', async () => {
    const first = deferred<string>()
    const latest = deferred<string>()
    const state = useAsync(vi.fn<() => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(latest.promise))

    const firstRequest = state.reload()
    const latestRequest = state.reload()
    latest.reject(new Error('latest failure'))
    await latestRequest
    first.resolve('stale order')
    await firstRequest

    expect(state.data.value).toBeNull()
    expect(state.error.value).toBe('latest failure')
    expect(state.loading.value).toBe(false)
  })
})
