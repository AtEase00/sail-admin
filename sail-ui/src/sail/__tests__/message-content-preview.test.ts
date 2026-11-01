// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '@/lib/api-client'

import MessageContentView from '../components/MessageContentView.vue'

vi.mock('@/lib/api-client', () => ({ apiFetch: vi.fn(), request: vi.fn() }))

describe('消息正文图片预览', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('净化外部地址，图片鉴权后才可预览，关闭消息释放地址', async () => {
    vi.mocked(apiFetch).mockResolvedValue(new Blob(['private-image'], { type: 'image/png' }))
    const revoke = vi.fn()
    const OriginalURL = URL
    // 用独立 URL 构造器隔离浏览器对象地址，不修改原生构造器。
    vi.stubGlobal('URL', class extends OriginalURL {
      static createObjectURL = vi.fn(() => 'blob:authorized-message-image')
      static revokeObjectURL = revoke
    })
    const wrapper = mount(MessageContentView, { props: { content: '<p>正文</p><img data-file-id="3" alt="说明图" src="https://external.invalid/track.png">', taskId: 9 }, global: { stubs: { ImagePreview: true } } })
    await flushPromises()
    expect(apiFetch).toHaveBeenLastCalledWith('/messages/tasks/9/attachments/3', { responseType: 'blob', query: { inline: true } })
    expect(wrapper.get('img').attributes('src')).toBe('blob:authorized-message-image')
    await wrapper.get('img').trigger('keydown', { key: 'Enter' })
    expect(wrapper.findComponent({ name: 'ImagePreview' }).props('src')).toBe('blob:authorized-message-image')
    await wrapper.setProps({ content: '<p>新消息</p>', taskId: 10 })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ImagePreview' }).exists()).toBe(false)
    expect(revoke).toHaveBeenCalledWith('blob:authorized-message-image')
    wrapper.unmount()
  })
})
