// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'

import { request } from '@/lib/api-client'

import type { InboxMessage } from '../message-center'

import MessageDetail from '../components/MessageDetail.vue'

vi.mock('@/lib/api-client', () => ({ request: vi.fn(), apiFetch: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: { success: vi.fn() } }))

/** 以真实详情组件验证打开期间的撤回和到期处理，只替换 HTTP 传输。 */
describe('个人消息详情实时状态', () => {
  afterEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  /** 通过异步响应和 Vue 更新完成一次界面刷新。 */
  async function settle() {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
  }

  /** 构造测试接口返回，正文在撤回后由服务端置空。 */
  function receipt(): InboxMessage {
    return { id: 8, taskId: 3, title: '状态通知', content: '<p>仅接收人可见的正文</p>', summary: '正文', category: 'NOTICE', priority: 'NORMAL', senderName: '发布人', readFlag: true, readAt: '2026-11-01 12:00:00', confirmedAt: null, archived: false, requireConfirmation: false, pinned: false, createdAt: '2026-11-01 11:00:00', expiresAt: null, taskStatus: 'SENT', actionPath: null, attachments: [] }
  }

  /** 抽屉壳不参与业务判断，保留内部真实正文与生命周期。 */
  function open() {
    const drawer = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) })
    return mount(MessageDetail, { props: { open: true, id: 8 }, global: { plugins: [createPinia()], stubs: { Drawer: drawer, ADrawer: drawer, RouterLink: true } } })
  }

  it('打开期间收到撤回事件会清除正文，卸载后不再请求', async () => {
    vi.mocked(request).mockResolvedValueOnce(receipt()).mockResolvedValueOnce({ ...receipt(), content: null, taskStatus: 'WITHDRAWN' })
    const wrapper = open()
    await settle()
    expect(wrapper.text()).toContain('仅接收人可见的正文')
    window.dispatchEvent(new Event('sail:messages-changed'))
    await settle()
    expect(wrapper.text()).toContain('发布者已撤回此消息')
    expect(wrapper.text()).not.toContain('仅接收人可见的正文')
    wrapper.unmount()
    window.dispatchEvent(new Event('sail:messages-changed'))
    expect(request).toHaveBeenCalledTimes(2)
  })

  it('到期时重新读取服务端状态并停止展示正文', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-01T12:00:00'))
    const active = { ...receipt(), expiresAt: '2026-11-01 12:00:01' }
    vi.mocked(request).mockResolvedValueOnce(active).mockResolvedValueOnce({ ...active, content: null })
    const wrapper = open()
    await settle()
    expect(wrapper.text()).toContain('仅接收人可见的正文')
    await vi.advanceTimersByTimeAsync(1100)
    await settle()
    expect(wrapper.text()).toContain('此消息已超过有效期')
    expect(wrapper.text()).not.toContain('仅接收人可见的正文')
    wrapper.unmount()
  })
})
