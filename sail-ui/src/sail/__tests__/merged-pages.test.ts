// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { request } from '@/lib/api-client'
import Jobs from '@/pages/system/jobs.vue'
import JobExecutions from '@/sail/components/JobExecutions.vue'
import { canAccessPage, navigation } from '@/sail/navigation'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/lib/api-client', () => ({ request: vi.fn() }))
vi.mock('ant-design-vue', () => ({ Modal: { confirm: vi.fn() } }))
const wrappers: ReturnType<typeof mount>[] = []
const container = { template: '<div><slot /><slot name="query" /><slot name="actions" /><slot name="pagination" /></div>' }

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  useAuthStore().identity = { user: { id: 1, nickname: '用户', username: 'user', status: 1, deptId: null }, roles: [], permissions: ['*'] }
  vi.mocked(request).mockResolvedValue({ records: [], total: 0 })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('合并工作流入口', () => {
  it.each([
    ['/system/jobs', 'system:job:log:query'],
    ['/system/jobs', 'system:job:query'],
    ['/messages/history', 'message:send'],
    ['/messages/history', 'message:query'],
  ])('单独拥有 %s 的 %s 权限也能进入，其他权限不放行', (path, permission) => {
    const entry = navigation.find(item => item.path === path)!
    useAuthStore().identity!.permissions = [permission]
    expect(canAccessPage(entry, useAuthStore())).toBe(true)
    useAuthStore().identity!.permissions = ['unrelated']
    expect(canAccessPage(entry, useAuthStore())).toBe(false)
  })

  it('仅有记录权限时默认打开记录，不挂载任务管理', async () => {
    useAuthStore().identity!.permissions = ['system:job:log:query']
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/system/jobs', component: Jobs }] })
    await router.push('/system/jobs')
    const wrapper = mount(Jobs, { global: { plugins: [router], stubs: { JobList: { template: '<div data-job-list />' }, JobExecutions: { template: '<div data-records />' } } } })
    wrappers.push(wrapper)
    expect(wrapper.find('[data-job-list]').exists()).toBe(false)
    expect(wrapper.find('[data-records]').exists()).toBe(true)
    expect(wrapper.findAll('[role="tab"]').map(tab => tab.text())).toEqual(['执行记录'])
  })

  it('单任务记录入口留在当前页面，并在同一筛选表单查询和重置全部条件', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/system/jobs', component: Jobs }] })
    await router.push('/system/jobs')
    const list = { emits: ['records'], template: '<button @click="$emit(\'records\', 42)">记录</button>' }
    const wrapper = mount(Jobs, { global: { plugins: [router], stubs: {
      JobList: list,
      AdvancedTable: container,
      ListPagination: true,
      DateRangePicker: true,
      Dialog: { template: '<div />' },
      FormSelect: { props: ['modelValue', 'options'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option value="">全部</option><option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option></select>' },
    } } })
    wrappers.push(wrapper)
    await wrapper.findAll('button').find(button => button.text() === '记录')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/jobs')
    expect(router.currentRoute.value.query).toMatchObject({ view: 'executions', jobId: '42' })
    expect(wrapper.findAll('form[role="search"]')).toHaveLength(1)
    const form = wrapper.get('form[role="search"]')
    expect((form.get('input[placeholder="全部任务"]').element as HTMLInputElement).value).toBe('42')
    await form.get('input[placeholder="任务名称或追踪号"]').setValue('诊断')
    await form.get('input[placeholder="全部任务"]').setValue('7')
    await form.findAll('select')[1]!.setValue('MANUAL')
    await form.trigger('submit')
    await flushPromises()
    expect(request).toHaveBeenLastCalledWith('/system/jobs/executions', 'GET', undefined, expect.objectContaining({ keyword: '诊断', jobId: 7, source: 'MANUAL' }))
    await form.findAll('button').find(button => button.text() === '重置')!.trigger('click')
    await flushPromises()
    const params = vi.mocked(request).mock.calls.at(-1)![3]
    expect(params).not.toHaveProperty('jobId')
    expect(params).not.toHaveProperty('source')
    expect(params).toHaveProperty('keyword', '')
  })

  it('执行记录离开页签后停止轮询', async () => {
    vi.useFakeTimers()
    try {
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/system/jobs', component: Jobs }] })
      await router.push('/system/jobs?view=executions')
      const active = ref(true)
      const shell = defineComponent({ setup: () => () => h(KeepAlive, null, () => active.value ? h(JobExecutions) : h('span')) })
      const wrapper = mount(shell, { global: { plugins: [router], stubs: { AdvancedTable: container, QueryBar: true, ListPagination: true, Dialog: { template: '<div />' } } } })
      wrappers.push(wrapper)
      await flushPromises()
      active.value = false
      await flushPromises()
      vi.mocked(request).mockClear()
      await vi.advanceTimersByTimeAsync(15000)
      expect(request).not.toHaveBeenCalled()
    }
    finally { vi.useRealTimers() }
  })
})
