import type { Component, PropType, VNode } from 'vue'

import { useSessionStorage } from '@vueuse/core'
import { computed, defineComponent, markRaw, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { navigation } from '@/sail/navigation'
import { useAuthStore } from '@/stores/auth'

/** 工作区标签与缓存共用页面标识，关闭标签时同步清理页面实例。 */
export function useWorkspaceTabs() {
  const auth = useAuthStore()
  const router = useRouter()
  const route = useRoute()
  const paths = useSessionStorage<string[]>(`sail:workspace-tabs:${auth.identity!.user.id}`, ['/dashboard'])
  const versions = ref<Record<string, number>>({})
  const wrappers = new Map<string, Component>()
  const allowed = computed(() => ['/dashboard', '/account', '/messages', ...navigation.filter(item => (!('admin' in item) || !item.admin || auth.isAdmin) && auth.can('permission' in item ? item.permission : undefined)).map(item => item.path)])
  const tabs = computed(() => paths.value.map(path => ({ path, title: String(router.resolve(path).meta.title ?? navigation.find(item => item.path === path)?.title ?? path), pinned: path === '/dashboard' })))

  /** 刷新代次进入缓存名称，使刷新与关闭都能释放旧实例。 */
  function cacheName(path: string) {
    return `SailPage_${path.replace(/[^a-z0-9]/gi, '_')}_${versions.value[path] ?? 0}`
  }
  const cacheNames = computed(() => paths.value.map(cacheName))
  /** 每个标签使用独立命名包装，避免同名路由组件互相覆盖缓存。 */
  function cachedView(path: string) {
    const name = cacheName(path)
    if (!wrappers.has(name)) {
      wrappers.set(name, markRaw(defineComponent({
        name,
        props: { view: { type: Object as PropType<VNode>, required: true } },
        setup(props) {
          return () => props.view
        },
      })))
    }
    return wrappers.get(name)!
  }
  /** 只刷新指定页面，其他已打开页面保留查询与表单状态。 */
  function refresh(path = route.path) {
    wrappers.delete(cacheName(path))
    versions.value[path] = (versions.value[path] ?? 0) + 1
  }
  /** 关闭当前标签时切换到相邻页面，工作台始终保留。 */
  async function close(path: string) {
    if (path === '/dashboard')
      return
    const index = paths.value.indexOf(path)
    if (route.path === path)
      await router.push(paths.value[index + 1] ?? paths.value[index - 1] ?? '/dashboard')
    paths.value = paths.value.filter(item => item !== path)
  }
  /** 批量关闭标签时先切换到保留页面，再让 KeepAlive 清理实例。 */
  async function closeGroup(path: string, kind: 'others' | 'left' | 'right' | 'all') {
    const index = paths.value.indexOf(path)
    const retained = paths.value.filter((item, position) => item === '/dashboard' || (kind === 'others' ? item === path : kind === 'left' ? position >= index : kind === 'right' ? position <= index : false))
    if (!retained.includes(route.path))
      await router.push(kind === 'all' ? '/dashboard' : path)
    paths.value = retained
  }
  // 会话存储只保存已授权的固定业务路径，权限变更后及时移除标签与缓存。
  watch([() => route.path, allowed], () => {
    const saved = Array.isArray(paths.value) ? paths.value : []
    paths.value = [...new Set(['/dashboard', ...saved.filter(path => typeof path === 'string' && allowed.value.includes(path)), ...(allowed.value.includes(route.path) ? [route.path] : [])])]
    for (const key of wrappers.keys()) {
      if (!cacheNames.value.includes(key))
        wrappers.delete(key)
    }
  }, { immediate: true })
  return { tabs, cacheNames, cacheName, cachedView, refresh, close, closeGroup }
}
