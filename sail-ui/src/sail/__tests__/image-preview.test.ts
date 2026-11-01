// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '@/lib/api-client'

import FilePreview from '../components/FilePreview.vue'
import ImagePreview from '../components/ImagePreview.vue'
import { imagePreviewCount } from '../image-preview-state'

vi.mock('@/lib/api-client', () => ({ apiFetch: vi.fn(), request: vi.fn() }))

describe('图片预览授权与浮层生命周期', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('关闭多个预览时按实例恢复焦点锁，不影响仍打开的预览', async () => {
    const first = mount(ImagePreview, { props: { src: '', open: true }, global: { stubs: { teleport: true, FocusScope: { template: '<div><slot /></div>' } } } })
    const second = mount(ImagePreview, { props: { src: '', open: true }, global: { stubs: { teleport: true, FocusScope: { template: '<div><slot /></div>' } } } })
    expect(imagePreviewCount.value).toBe(2)
    first.unmount()
    expect(imagePreviewCount.value).toBe(1)
    await second.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(second.emitted('close')).toHaveLength(1)
    second.unmount()
    expect(imagePreviewCount.value).toBe(0)
  })

  it('消息图片沿用任务授权，旧响应不会覆盖新图片，关闭时释放 Blob 地址', async () => {
    let resolveOld!: (value: Blob) => void
    vi.mocked(apiFetch).mockReturnValueOnce(new Promise<Blob>((resolve) => {
      resolveOld = resolve
    })).mockResolvedValueOnce(new Blob(['new'], { type: 'image/png' }))
    const create = vi.fn(() => 'blob:current-image')
    const revoke = vi.fn()
    // 独立构造器确保测试结束后恢复全局 URL，不污染其他组件的对象地址行为。
    const OriginalURL = URL
    vi.stubGlobal('URL', class extends OriginalURL {
      static createObjectURL = create
      static revokeObjectURL = revoke
    })
    const wrapper = mount(FilePreview, { props: { file: { id: 1, taskId: 7, name: 'old.png' } }, global: { stubs: { ImagePreview: true, Dialog: { template: '<div />' } } } })
    await wrapper.setProps({ file: { id: 2, taskId: 7, name: 'new.png' } })
    await flushPromises()
    expect(apiFetch).toHaveBeenLastCalledWith('/messages/tasks/7/attachments/2', { responseType: 'blob', query: { inline: true } })
    resolveOld(new Blob(['old'], { type: 'image/png' }))
    await flushPromises()
    expect(create).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent({ name: 'ImagePreview' }).props('src')).toBe('blob:current-image')
    await wrapper.setProps({ file: undefined })
    expect(revoke).toHaveBeenCalledWith('blob:current-image')
    wrapper.unmount()
  })
})
