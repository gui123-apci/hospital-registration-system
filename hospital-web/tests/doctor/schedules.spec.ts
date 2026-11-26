import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http } from '@/api/http'
import ScheduleView from '@/views/doctor/ScheduleView.vue'
import type { Schedule } from '@/types/models'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: pushMock }) }))

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return {
    ...actual,
    http: { get: vi.fn(), post: vi.fn() }
  }
})

const today = (() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})()

const schedules: Schedule[] = [
  { id: 101, doctorId: 1, departmentId: 2, workDate: today, timeSlot: 1, totalNum: 20, leftNum: 18, price: 50, status: 1 },
  { id: 102, doctorId: 1, departmentId: 2, workDate: '2099-01-01', timeSlot: 2, totalNum: 20, leftNum: 0, price: 50, status: 1 },
  { id: 103, doctorId: 1, departmentId: 2, workDate: '2099-01-02', timeSlot: 3, totalNum: 10, leftNum: 10, price: 50, status: 0 }
]

function mountView() {
  return mount(ScheduleView)
}

function buttonWithText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('医生排班页', () => {
  it('渲染今天的排班，显示剩余号源和时段', async () => {
    vi.mocked(http.get).mockResolvedValueOnce(schedules)
    const wrapper = mountView()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('今天')
    expect(text).toContain('后续排班')
    expect(text).toContain('上午')
    expect(text).toContain('08:00—12:00')
    expect(text).toContain('18 / 20')
    expect(text).toContain('出诊中')
  })

  it('点「进入工作台」跳到工作台路由', async () => {
    vi.mocked(http.get).mockResolvedValueOnce(schedules)
    const wrapper = mountView()
    await flushPromises()

    await buttonWithText(wrapper, '进入工作台')!.trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/doctor/workbench')
  })

  it('停诊和约满的排班标出状态，停诊的按钮禁用', async () => {
    vi.mocked(http.get).mockResolvedValueOnce(schedules)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('已约满')
    expect(wrapper.text()).toContain('已停诊')

    const cards = wrapper.findAll('.schedule-card')
    const stoppedButton = cards[2].find('button')
    expect(stoppedButton.attributes('disabled')).toBeDefined()
  })

  it('没有排班时显示空状态', async () => {
    vi.mocked(http.get).mockResolvedValueOnce([])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无排班')
  })

  it('加载失败显示错误，点重试会重新请求', async () => {
    vi.mocked(http.get).mockRejectedValueOnce(new Error('网络异常'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('排班加载失败：网络异常')

    vi.mocked(http.get).mockResolvedValueOnce(schedules)
    await buttonWithText(wrapper, '重试')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('上午')
    expect(vi.mocked(http.get).mock.calls.filter(([url]) => url === '/doctor-work/schedules')).toHaveLength(2)
  })
})
