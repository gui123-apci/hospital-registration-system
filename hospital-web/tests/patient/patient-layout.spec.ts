import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientLayout from '@/layouts/PatientLayout.vue'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const tabRouteNames = ['patient-home', 'patient-departments', 'patient-orders', 'patient-patients'] as const
let layoutRouter: ReturnType<typeof createRouter>

function mountLayout() {
  return mount(PatientLayout, {
    global: {
      plugins: [layoutRouter],
      stubs: { RouterView: true }
    }
  })
}

describe('PatientLayout bottom navigation', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 1, username: '测试患者', role: 0, roleName: '患者' }
    layoutRouter = createRouter({
      history: createMemoryHistory(),
      routes: tabRouteNames.map((name) => {
        const registered = router.getRoutes().find((route) => route.name === name)
        if (!registered) throw new Error(`Missing patient route: ${name}`)
        return { path: registered.path, name: registered.name, component: { template: '<div />' } }
      })
    })
    await layoutRouter.push('/patient/home')
  })

  it('links all four tabs to registered patient routes', () => {
    const wrapper = mountLayout()
    const links = wrapper.findAll('a.patient-tab')

    expect(links.map((link) => link.text())).toEqual(['首页', '挂号', '订单', '我的'])
    expect(links.map((link) => link.attributes('href'))).toEqual([
      '/patient/home',
      '/patient/departments',
      '/patient/orders',
      '/patient/patients'
    ])
    expect(links.map((link) => router.resolve(link.attributes('href')!).name)).toEqual([
      'patient-home',
      'patient-departments',
      'patient-orders',
      'patient-patients'
    ])
  })

  it('marks only the current patient route as active', async () => {
    const wrapper = mountLayout()
    expect(wrapper.get('a[href="/patient/home"]').classes()).toContain('is-active')

    await layoutRouter.push('/patient/orders')
    await wrapper.vm.$nextTick()

    const activeLinks = wrapper.findAll('a.patient-tab.is-active')
    expect(activeLinks).toHaveLength(1)
    expect(activeLinks[0].text()).toBe('订单')
  })
})
