// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, reactive, ref } from 'vue'

import { useWorkspaceTabs } from '@/sail/use-workspace-tabs'
import { useAuthStore } from '@/stores/auth'

const routing = vi.hoisted(() => ({ route: { path: '/account' } }))
vi.mock('vue-router', () => ({
  useRoute: () => routing.route,
  useRouter: () => ({ resolve: (path: string) => ({ meta: { title: path } }) }),
}))

describe('工作区页面根节点与缓存', () => {
  beforeEach(() => {
    sessionStorage.clear()
    routing.route = reactive({ path: '/account' })
    setActivePinia(createPinia())
    useAuthStore().identity = { user: { id: 1, nickname: '用户', username: 'user', status: 1, deptId: null }, roles: [], permissions: [] }
  })

  it('多根页面可参与过渡，返回标签保留状态，刷新仅重建当前页', async () => {
    const page = defineComponent({
      setup() {
        const value = ref('')
        function update(e: Event) {
          value.value = (e.target as HTMLInputElement).value
        }
        return () => [h('input', { value: value.value, onInput: update }), h('p', '多根页面')]
      },
    })
    let workspace!: ReturnType<typeof useWorkspaceTabs>
    const shell = defineComponent({
      setup() {
        workspace = useWorkspaceTabs()
        return () => h(KeepAlive, { include: workspace.cacheNames.value }, () => h(workspace.cachedView(routing.route.path), { key: workspace.cacheName(routing.route.path), view: h(page) }))
      },
    })
    const wrapper = mount(shell)
    expect(wrapper.element.tagName).toBe('DIV')
    await wrapper.get('input').setValue('尚未保存的昵称')
    routing.route.path = '/messages'
    await flushPromises()
    expect(wrapper.get('input').element.value).toBe('')
    routing.route.path = '/account'
    await flushPromises()
    expect(wrapper.get('input').element.value).toBe('尚未保存的昵称')
    workspace.refresh()
    await flushPromises()
    expect(wrapper.get('input').element.value).toBe('')
    wrapper.unmount()
  })
})
