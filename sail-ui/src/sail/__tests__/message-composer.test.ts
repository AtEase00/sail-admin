// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import { request } from '@/lib/api-client'
import { message } from '@/lib/message'
import MessageComposer from '@/sail/components/MessageComposer.vue'
import { useAuthStore } from '@/stores/auth'

const confirmation = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/lib/api-client', () => ({ request: vi.fn(), apiFetch: vi.fn() }))
vi.mock('ant-design-vue', () => ({
  Modal: { template: '<div />', confirm: confirmation.confirm },
  Input: { props: ['value'], emits: ['update:value'], template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)">' },
  Select: { template: '<select />' },
  Switch: { template: '<button type="button" />' },
  DatePicker: { template: '<input />' },
}))
vi.mock('@/sail/components/MessageTheme.vue', () => ({ default: { template: '<div><slot /></div>' } }))
const wrappers: ReturnType<typeof mount>[] = []
async function render() {
  const page = defineComponent({ components: { MessageComposer }, template: '<MessageComposer v-if="$route.query.compose === \'1\'" />' })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/messages/history', component: page }] })
  await router.push('/messages/history?compose=1')
  const wrapper = mount(RouterView, { global: { plugins: [router], stubs: { MessageEditor: true, MessageRecipients: true, FilePreview: true, FileAttachment: true, MessageContentView: true } } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  useAuthStore().identity = { user: { id: 1, nickname: '用户', username: 'user', status: 1, deptId: null }, roles: [], permissions: ['message:send'] }
  vi.mocked(request).mockImplementation(async path => path === '/messages/settings' ? { maxAttachments: 5 } : [])
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  message.clear()
})

describe('发布记录内的消息编辑器', () => {
  it('同一页面关闭抽屉时确认未保存修改，取消后保留输入', async () => {
    const { wrapper, router } = await render()
    await wrapper.get('input[placeholder="消息标题"]').setValue('未保存的消息')
    const cancelled = router.push('/messages/history')
    await flushPromises()
    expect(confirmation.confirm).toHaveBeenCalledOnce()
    confirmation.confirm.mock.calls[0]![0].onCancel()
    await cancelled
    expect(router.currentRoute.value.query.compose).toBe('1')
    expect((wrapper.get('input[placeholder="消息标题"]').element as HTMLInputElement).value).toBe('未保存的消息')
    const accepted = router.push('/messages/history')
    await flushPromises()
    confirmation.confirm.mock.calls[1]![0].onOk()
    await accepted
    await flushPromises()
    expect(router.currentRoute.value.query.compose).toBeUndefined()
    expect(wrapper.find('input[placeholder="消息标题"]').exists()).toBe(false)
  })

  it('保存草稿更新抽屉地址，不离开发布记录，也不重复读取或弹出离开确认', async () => {
    const { wrapper, router } = await render()
    await wrapper.get('input[placeholder="消息标题"]').setValue('草稿标题')
    vi.mocked(request).mockResolvedValueOnce({ id: 15, version: 1, updatedAt: '2026-11-04 01:00:00' })
    await wrapper.findAll('button').find(button => button.text() === '保存草稿')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/messages/history')
    expect(router.currentRoute.value.query).toEqual({ compose: '1', draft: '15' })
    expect(confirmation.confirm).not.toHaveBeenCalled()
    expect(request).toHaveBeenLastCalledWith('/messages/tasks', 'POST', expect.objectContaining({ title: '草稿标题' }))
    await router.push('/messages/history')
    expect(confirmation.confirm).not.toHaveBeenCalled()
  })
})
