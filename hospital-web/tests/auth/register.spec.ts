import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { routerKey } from 'vue-router'
import { ApiError, http } from '@/api/http'
import RegisterView from '@/views/auth/RegisterView.vue'
import type { LoginResponse, UserInfo } from '@/types/models'

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

const registeredUser: UserInfo = {
  id: 15,
  username: '13900001234',
  role: 0,
  roleName: '患者'
}

const loginResponse: LoginResponse = {
  token: 'registered-token',
  tokenType: 'Bearer',
  expiresIn: 7200,
  user: registeredUser
}

function mountRegister() {
  const push = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(RegisterView, {
    global: {
      provide: { [routerKey]: { push } },
      stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } }
    }
  })
  return { wrapper, push }
}

async function submitRegistration(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('input[name="phone"]').setValue('13900001234')
  await wrapper.get('input[name="password"]').setValue('123456')
  await wrapper.get('input[name="confirmPassword"]').setValue('123456')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  it('registers, logs in with the same credentials, and opens patient home', async () => {
    vi.mocked(http.post)
      .mockResolvedValueOnce(registeredUser)
      .mockResolvedValueOnce(loginResponse)
    const { wrapper, push } = mountRegister()

    await submitRegistration(wrapper)

    expect(http.post).toHaveBeenNthCalledWith(1, '/auth/register', {
      phone: '13900001234',
      password: '123456'
    })
    expect(http.post).toHaveBeenNthCalledWith(2, '/auth/login', {
      username: '13900001234',
      password: '123456'
    })
    expect(sessionStorage.getItem('hospital_token')).toBe('registered-token')
    expect(push).toHaveBeenCalledTimes(1)
    expect(push).toHaveBeenCalledWith('/patient/home')
  })

  it('does not navigate or persist a token when login after registration fails', async () => {
    vi.mocked(http.post)
      .mockResolvedValueOnce(registeredUser)
      .mockRejectedValueOnce(new ApiError(3001, '账号或密码错误'))
    const { wrapper, push } = mountRegister()

    await submitRegistration(wrapper)

    expect(http.post).toHaveBeenCalledTimes(2)
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('.auth-error').text()).toContain('账号或密码错误')
  })

  it('shows registration errors directly without attempting login', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new ApiError(4001, '该手机号已注册'))
    const { wrapper, push } = mountRegister()

    await submitRegistration(wrapper)

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem('hospital_token')).toBeNull()
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('.auth-error').text()).toContain('该手机号已注册')
  })
})
