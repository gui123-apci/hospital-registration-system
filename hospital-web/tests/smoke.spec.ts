import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import App from '@/App.vue'
import router from '@/router'

describe('frontend scaffold', () => {
  it('runs Vitest in jsdom', () => {
    expect(typeof document).toBe('object')
    expect(sessionStorage).toBeDefined()
  })

  it('mounts the real App shell with router and pinia and renders the login route', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    sessionStorage.clear()
    await router.replace('/login')

    const wrapper = mount(App, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.get('#login-title').text()).toBe('欢迎回来')
    expect(wrapper.find('form').exists()).toBe(true)
    wrapper.unmount()
  }, 20000)
})
