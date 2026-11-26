import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PaymentCountdown from '@/components/PaymentCountdown.vue'

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('PaymentCountdown', () => {
  it('counts down by seconds and never goes below zero', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    const wrapper = mount(PaymentCountdown, {
      props: { deadline: '2026-11-25T09:01:00' }
    })
    expect(wrapper.text()).toContain('01:00')
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(wrapper.text()).toContain('00:59')
    await vi.advanceTimersByTimeAsync(61000)
    await nextTick()
    expect(wrapper.text()).toContain('00:00')
    wrapper.unmount()
  })

  it('uses ceiling seconds and does not expire during the final fractional second', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    const wrapper = mount(PaymentCountdown, {
      props: { deadline: '2026-11-25T09:00:00.500' }
    })

    expect(wrapper.text()).toBe('00:01')
    expect(wrapper.emitted('expired')).toBeUndefined()
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()

    expect(wrapper.text()).toBe('00:00')
    expect(wrapper.emitted('expired')).toHaveLength(1)
    wrapper.unmount()
  })

  it('shows an accessible countdown label', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    const wrapper = mount(PaymentCountdown, {
      props: { deadline: '2026-11-25T09:01:00' }
    })
    expect(wrapper.attributes('aria-label')).toBe('支付剩余时间 01:00')
    expect(wrapper.text()).toBe('01:00')
  })

  it('renders no countdown text or blank accessible label when the deadline is absent', () => {
    const wrapper = mount(PaymentCountdown)
    expect(wrapper.text()).toBe('')
    expect(wrapper.attributes('aria-label')).toBeUndefined()
    wrapper.unmount()
  })

  it('clears its countdown timer when unmounted', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T09:00:00'))
    const clearInterval = vi.spyOn(window, 'clearInterval')
    const wrapper = mount(PaymentCountdown, {
      props: { deadline: '2026-11-25T09:01:00' }
    })
    expect(vi.getTimerCount()).toBe(1)

    wrapper.unmount()

    expect(clearInterval).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })
})
