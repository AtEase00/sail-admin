// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'

import { request } from '@/lib/api-client'
import { message, messageItems } from '@/lib/message'
import Account from '@/pages/account.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/lib/api-client', () => ({ request: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))

const user = { id: 1, username: 'worker', nickname: '工作人员', deptId: null, status: 1 }
const profile = { user, department: null, posts: [], createdAt: '2026-11-03T10:00:00' }
function render() {
  const visible = ref(true)
  const shell = defineComponent({ setup: () => () => h(KeepAlive, null, () => visible.value ? h(Account) : h('div')) })
  return { visible, wrapper: mount(shell, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } }) }
}

describe('个人中心', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    message.clear()
    setActivePinia(createPinia())
    useAuthStore().token = 'session'
    useAuthStore().identity = { user: { ...user }, roles: ['WORKER'], permissions: [] }
    vi.mocked(request).mockImplementation(async () => structuredClone(profile))
  })
  it('只提交昵称并同步菜单身份，离开缓存页清空密码但保留资料草稿', async () => {
    const { wrapper, visible } = render()
    await flushPromises()
    await wrapper.get('#profile-nickname').setValue(' 新昵称 ')
    vi.mocked(request).mockResolvedValueOnce({ ...user, nickname: '新昵称' })
    await wrapper.findAll('form')[0]!.trigger('submit')
    await flushPromises()
    expect(request).toHaveBeenCalledWith('/system/auth/profile', 'PUT', { nickname: '新昵称' })
    expect(useAuthStore().identity?.user.nickname).toBe('新昵称')
    await wrapper.get('#profile-nickname').setValue('待保存')
    await wrapper.get('#old-password').setValue('TemporaryPassword')
    visible.value = false
    await flushPromises()
    visible.value = true
    await flushPromises()
    expect((wrapper.get('#old-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('#profile-nickname').element as HTMLInputElement).value).toBe('待保存')
    wrapper.unmount()
  })
  it('加载失败提供重试，保存失败保留输入并展示原因', async () => {
    vi.mocked(request).mockRejectedValueOnce(new Error('加载暂不可用'))
    const { wrapper } = render()
    await flushPromises()
    expect(messageItems.value.at(-1)?.content).toBe('加载暂不可用')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    const reload = wrapper.findAll('button').find(button => button.text() === '重新加载')!
    await reload.trigger('click')
    await flushPromises()
    await wrapper.get('#profile-nickname').setValue('保留输入')
    vi.mocked(request).mockRejectedValueOnce(new Error('保存暂不可用'))
    await wrapper.findAll('form')[0]!.trigger('submit')
    await flushPromises()
    expect(messageItems.value.at(-1)?.content).toBe('保存暂不可用')
    expect((wrapper.get('#profile-nickname').element as HTMLInputElement).value).toBe('保留输入')
    expect(useAuthStore().identity?.user.nickname).toBe('工作人员')
    wrapper.unmount()
  })
})
