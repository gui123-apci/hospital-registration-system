import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NameIdSelect from '@/components/NameIdSelect.vue'
import { nextTick } from 'vue'

const options = [
  { label: '呼吸内科', value: 11 },
  { label: '心血管内科', value: 12 }
]

describe('NameIdSelect', () => {
  it('renders the supplied options in the dropdown and emits the numeric id', async () => {
    const wrapper = mount(NameIdSelect, {
      props: { modelValue: undefined, options, placeholder: '请选择科室', 'aria-label': '选择科室' },
      attachTo: document.body
    })

    await wrapper.get('.el-select').trigger('click')
    await nextTick()
    const items = Array.from(document.querySelectorAll<HTMLElement>('.el-select-dropdown__item'))
    expect(items.map((item) => item.textContent?.trim())).toEqual(['呼吸内科', '心血管内科'])

    items[1].click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([12])
    wrapper.unmount()
  })

  it('forwards the current value and disabled state to the inner select', () => {
    const wrapper = mount(NameIdSelect, {
      props: { modelValue: 11, options, disabled: true, placeholder: '选择排班' }
    })

    const select = wrapper.findComponent({ name: 'ElSelect' })
    expect(select.props('modelValue')).toBe(11)
    expect(select.props('disabled')).toBe(true)
    expect(select.props('placeholder')).toBe('选择排班')
  })
})
