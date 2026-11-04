// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { useMessageError } from '@/composables/use-message-error'
import { ApiError } from '@/lib/api-client'
import { message, messageItems } from '@/lib/message'
import GlobalMessage from '@/sail/components/GlobalMessage.vue'
import SailForm from '@/sail/components/SailForm.vue'

beforeEach(() => {
  vi.useFakeTimers()
  message.clear()
})
afterEach(() => {
  message.clear()
  vi.useRealTimers()
})

describe('全局 Message', () => {
  it('四类反馈渲染在同一个全局容器，错误和警告即时播报', async () => {
    const wrapper = mount(GlobalMessage, { attachTo: document.body })
    try {
      for (const type of ['success', 'error', 'warning', 'info'] as const) {
        message.clear()
        message[type]('测试反馈')
        await nextTick()
        const notice = document.querySelector(`[data-global-message] [data-message-type="${type}"]`)!
        expect(notice.textContent).toBe('测试反馈')
        expect(notice.getAttribute('role')).toBe(['error', 'warning'].includes(type) ? 'alert' : 'status')
      }
    }
    finally { wrapper.unmount() }
  })

  it('同错只弹一次，不无限延长消息；自动退出并限制同时显示数量', () => {
    const first = message.error('保存失败')
    vi.advanceTimersByTime(2000)
    expect(message.error('保存失败')).toBe(first)
    expect(messageItems.value).toHaveLength(1)
    vi.advanceTimersByTime(2500)
    expect(messageItems.value).toHaveLength(0)
    for (const content of ['一', '二', '三', '四']) message.success(content)
    expect(messageItems.value.map(item => item.content)).toEqual(['二', '三', '四'])
    vi.advanceTimersByTime(3000)
    expect(messageItems.value).toHaveLength(0)
  })

  it('业务错误保留诊断字段，用户提示不带追踪号', () => {
    const failure = new ApiError('账号或密码错误', 400, 'trace-private-id')
    expect(failure.traceId).toBe('trace-private-id')
    expect(failure.message).toBe('账号或密码错误')
    message.error(failure)
    message.error('账号或密码错误（追踪号 trace-private-id）')
    expect(messageItems.value).toHaveLength(1)
    expect(messageItems.value[0]?.content).toBe('账号或密码错误')
  })

  it('保留失败状态，连续后台失败不刷屏，清除后可以重新反馈', () => {
    const error = useMessageError()
    error.value = '读取失败'
    expect(error.value).toBe('读取失败')
    vi.advanceTimersByTime(4500)
    error.value = '读取失败'
    expect(messageItems.value).toHaveLength(0)
    error.value = ''
    error.value = '读取失败'
    expect(messageItems.value).toHaveLength(1)
  })

  it('一次表单提交只提示首个无效字段并聚焦，修正后清除无效标记', async () => {
    const wrapper = mount(SailForm, { attachTo: document.body, slots: { default: '<label for="first">账号</label><input id="first" required><label for="second">密码</label><input id="second" required>' } })
    try {
      const fields = wrapper.findAll('input')
      for (const field of fields) field.element.dispatchEvent(new Event('invalid', { cancelable: true }))
      await nextTick()
      expect(messageItems.value).toHaveLength(1)
      expect(messageItems.value[0]).toMatchObject({ type: 'warning', content: '账号：请填写或选择此项' })
      expect(document.activeElement).toBe(fields[0]!.element)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      await fields[0]!.setValue('admin')
      expect(fields[0]!.attributes('aria-invalid')).toBeUndefined()
    }
    finally { wrapper.unmount() }
  })
})
