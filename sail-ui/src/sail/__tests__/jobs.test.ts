// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive } from 'vue'

import type { JobDefinition } from '@/sail/jobs'

import { request } from '@/lib/api-client'
import { message, messageItems } from '@/lib/message'
import Jobs from '@/sail/components/JobList.vue'
import { canRunJob, jobDefaults, jobPayload } from '@/sail/jobs'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/lib/api-client', () => ({ request: vi.fn() }))
const confirmation = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('ant-design-vue', () => ({ Modal: confirmation }))

const row: JobDefinition = { ...jobDefaults(), id: 1, name: '诊断任务', handler: 'system.diagnostic', version: 2, status: 'ENABLED', syncStatus: 'SYNCED', syncError: null, activeRunId: null, nextFireAt: null, lastStartedAt: null, lastStatus: null }
const wrappers: ReturnType<typeof mount>[] = []
const container = { template: '<div><slot /><slot name="query" /><slot name="actions" /><slot name="before" /><slot name="pagination" /></div>' }
function render() {
  const shell = defineComponent({ setup: () => () => h(KeepAlive, null, () => h(Jobs)) })
  const wrapper = mount(shell, { global: { stubs: {
    AdvancedTable: container,
    Dialog: { props: ['open'], template: '<div v-if="open"><slot /></div>' },
    DialogContent: container,
    DialogHeader: container,
    DialogDescription: container,
    DialogTitle: container,
    FormSelect: { props: ['modelValue', 'options'], template: '<select :value="modelValue"><option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option></select>' },
    QueryBar: true,
    ListPagination: true,
    RouterLink: { template: '<a><slot /></a>' },
  } } })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find(b => b.text() === label)!
}

describe('定时任务操作边界', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    message.clear()
    setActivePinia(createPinia())
    useAuthStore().identity = { user: { id: 1, nickname: '管理员', username: 'admin', status: 1, deptId: null }, roles: ['SUPER_ADMIN'], permissions: ['*'] }
    vi.mocked(request).mockImplementation(async (path, method) => {
      if (path === '/system/jobs/handlers')
        return [{ code: 'system.diagnostic', name: '诊断', description: '无副作用', retryable: true }]
      if (path === '/system/jobs' && (!method || method === 'GET'))
        return { records: [structuredClone(row)], total: 1, page: 1, size: 20 }
      return 1
    })
  })
  afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

  it('运行入口同时要求启用、同步完成、无占用；提交不携带内部状态', () => {
    expect(canRunJob(row)).toBe(true)
    for (const patch of [{ status: 'PAUSED' }, { syncStatus: 'PENDING' }, { syncStatus: 'ERROR' }, { activeRunId: 99 }])
      expect(canRunJob({ ...row, ...patch } as JobDefinition)).toBe(false)
    const payload = jobPayload(row)
    expect(payload).not.toHaveProperty('status')
    expect(payload).not.toHaveProperty('activeRunId')
    expect(payload).toHaveProperty('version', 2)
  })

  it('没有控制和执行权限的用户只能查看任务', async () => {
    useAuthStore().identity!.permissions = ['system:job:query']
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('诊断任务')
    for (const label of ['新增任务', '编辑', '暂停', '立即执行', '归档'])
      expect(button(wrapper, label)).toBeUndefined()
  })

  it('手动运行经过确认并携带版本，冲突后显示服务端原因', async () => {
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '立即执行').trigger('click')
    expect(request).not.toHaveBeenCalledWith('/system/jobs/1/run', expect.anything(), expect.anything())
    vi.mocked(request).mockRejectedValueOnce(new Error('已有任务执行中'))
    const options = confirmation.confirm.mock.calls[0]![0]
    await expect(options.onOk()).rejects.toThrow('已有任务执行中')
    await flushPromises()
    expect(request).toHaveBeenCalledWith('/system/jobs/1/run', 'POST', { version: 2 })
    expect(messageItems.value.at(-1)?.content).toBe('已有任务执行中')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('编辑保存失败保留输入与版本，用户可以修正重试', async () => {
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '编辑').trigger('click')
    await wrapper.get('#job-name').setValue('修改后的任务')
    vi.mocked(request).mockRejectedValueOnce(new Error('任务版本已更新'))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(messageItems.value.at(-1)?.content).toBe('任务版本已更新')
    expect((wrapper.get('#job-name').element as HTMLInputElement).value).toBe('修改后的任务')
    const payload = vi.mocked(request).mock.calls.find(([path, method]) => path === '/system/jobs/1' && method === 'PUT')![2]
    expect(payload).toMatchObject({ name: '修改后的任务', version: 2 })
    expect(payload).not.toHaveProperty('syncStatus')
  })
})
