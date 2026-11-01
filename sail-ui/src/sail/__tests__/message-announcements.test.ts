// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { request } from '@/lib/api-client'

import MessageAnnouncements from '../components/MessageAnnouncements.vue'

vi.mock('@/lib/api-client', () => ({ request: vi.fn() }))
const detail = defineComponent({ name: 'MessageDetail', props: ['id', 'open'], emits: ['presented', 'update:open'], template: '<div />' })

/** 检查真实公告队列组件的去重和会话边界，HTTP 与详情壳单独替换。 */
describe('主动弹窗公告', () => {
  afterEach(() => vi.clearAllMocks())

  it('逐条展示，已展示公告即使补读仍不会重复弹出', async () => {
    vi.mocked(request).mockImplementation(async path => path === '/messages/announcements' ? [{ id: 1 }, { id: 2 }] : null)
    const wrapper = mount(MessageAnnouncements, { global: { stubs: { MessageDetail: detail } } })
    await flushPromises()
    const popup = wrapper.findComponent(detail)
    expect(popup.props()).toMatchObject({ id: 1, open: true })
    popup.vm.$emit('presented', 1)
    await flushPromises()
    expect(request).toHaveBeenCalledWith('/messages/mine/1/presented', 'PUT')
    popup.vm.$emit('update:open', false)
    await flushPromises()
    expect(popup.props()).toMatchObject({ id: 2, open: true })
    popup.vm.$emit('presented', 2)
    await flushPromises()
    popup.vm.$emit('update:open', false)
    await flushPromises()
    expect(popup.props('open')).toBe(false)
    wrapper.unmount()
  })

  it('会话失效关闭公告，卸载后实时事件不会继续请求', async () => {
    vi.mocked(request).mockResolvedValue([{ id: 3 }])
    const wrapper = mount(MessageAnnouncements, { global: { stubs: { MessageDetail: detail } } })
    await flushPromises()
    window.dispatchEvent(new Event('sail:session-expired'))
    await flushPromises()
    expect(wrapper.findComponent(detail).props('open')).toBe(false)
    wrapper.unmount()
    const count = vi.mocked(request).mock.calls.length
    window.dispatchEvent(new Event('sail:messages-changed'))
    await flushPromises()
    expect(request).toHaveBeenCalledTimes(count)
  })
})
