import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { routerKey } from 'vue-router'
import { http } from '@/api/http'
import LoginView from '@/views/auth/LoginView.vue'
import type { LoginResponse, Role } from '@/types/models'

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return {
    ...actual,
    http: {
      get: vi.fn(),
      post: vi.fn()
    }
  }
})

function loginResponse(role: Role = 0, username = '13800000000'): LoginResponse {
  return {
    token: 'token-1',
    tokenType: 'Bearer',
    expiresIn: 7200,
    user: { id: 1, username, role, roleName: role === 0 ? '患者' : role === 1 ? '管理员' : '医生' }
  }
}

function mountLogin() {
  const push = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(LoginView, {
    global: {
      provide: { [routerKey]: { push } },
      stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } }
    }
  })
  return { wrapper, push }
}

async function submitLogin(wrapper: ReturnType<typeof mount>, username: string, password = '123456') {
  await wrapper.get('input[name="username"]').setValue(username)
  await wrapper.get('input[name="password"]').setValue(password)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(http.post).mockResolvedValue(loginResponse())
  })

  it('presents a phone-or-account text field', () => {
    const { wrapper } = mountLogin()
    const account = wrapper.get('input[name="username"]')

    expect(wrapper.text()).toContain('手机号或账号')
    expect(account.attributes('type')).toBe('text')
    expect(account.attributes('placeholder')).toBe('请输入手机号或账号')
  })

  it.each(['', '   '])('rejects invalid account %j without calling auth', async (username) => {
    const { wrapper, push } = mountLogin()

    await submitLogin(wrapper, username)

    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.find('.auth-error').exists()).toBe(true)
    expect(push).not.toHaveBeenCalled()
  })

  it('rejects a trimmed account longer than 32 characters without calling auth', async () => {
    const { wrapper, push } = mountLogin()

    await submitLogin(wrapper, `  ${'a'.repeat(33)}  `)

    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.get('.auth-error').text()).toContain('账号长度不能超过 32 位')
    expect(push).not.toHaveBeenCalled()
  })

  it.each(['', '12345', '123456789012345678901'])('preserves the 6–20 character password rule for %j', async (password) => {
    const { wrapper, push } = mountLogin()

    await submitLogin(wrapper, 'doctor01', password)

    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.get('.auth-error').text()).toContain('密码长度需为 6–20 位')
    expect(push).not.toHaveBeenCalled()
  })

  it.each([
    [0, '13800000000', '/patient/home'],
    [1, 'admin', '/admin/departments'],
    [2, 'doctor01', '/doctor/workbench']
  ] as const)('submits role %i account %s and navigates to %s', async (role, username, homePath) => {
    vi.mocked(http.post).mockResolvedValue(loginResponse(role, username))
    const { wrapper, push } = mountLogin()

    await submitLogin(wrapper, username)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/auth/login', { username, password: '123456' })
    expect(push).toHaveBeenCalledTimes(1)
    expect(push).toHaveBeenCalledWith(homePath)
  })

  it('accepts, trims, and submits a 32-character account with surrounding whitespace', async () => {
    const username = 'a'.repeat(32)
    const { wrapper, push } = mountLogin()
    const paddedUsername = `  ${username}  `

    await wrapper.get('input[name="username"]').setValue(paddedUsername)
    expect(wrapper.get('input[name="username"]').element).toHaveProperty('value', paddedUsername)
    expect(wrapper.get('input[name="username"]').attributes('maxlength')).toBeUndefined()
    await wrapper.get('input[name="password"]').setValue('123456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/auth/login', { username, password: '123456' })
    expect(push).toHaveBeenCalledWith('/patient/home')
  })

  it('trims surrounding whitespace from a valid account', async () => {
    vi.mocked(http.post).mockResolvedValue(loginResponse(2, 'doctor01'))
    const { wrapper, push } = mountLogin()

    await submitLogin(wrapper, '  doctor01  ')

    expect(http.post).toHaveBeenCalledWith('/auth/login', { username: 'doctor01', password: '123456' })
    expect(push).toHaveBeenCalledWith('/doctor/workbench')
  })

  it('blocks duplicate submissions while login is pending', async () => {
    let finishLogin!: (value: LoginResponse) => void
    vi.mocked(http.post).mockReturnValue(new Promise((resolve) => {
      finishLogin = resolve
    }))
    const { wrapper, push } = mountLogin()
    await wrapper.get('input[name="username"]').setValue('doctor01')
    await wrapper.get('input[name="password"]').setValue('123456')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(http.post).toHaveBeenCalledTimes(1)

    finishLogin(loginResponse(2, 'doctor01'))
    await flushPromises()
    expect(push).toHaveBeenCalledTimes(1)
    expect(push).toHaveBeenCalledWith('/doctor/workbench')
  })
})
