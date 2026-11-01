<script setup lang="ts">
import { ChevronRight, Maximize, Minimize, RefreshCw } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { Button } from '@/components/ui/button'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import AppSidebar from '@/sail/components/AppSidebar.vue'
import MessageAnnouncements from '@/sail/components/MessageAnnouncements.vue'
import MessageBell from '@/sail/components/MessageBell.vue'
import ThemeSettings from '@/sail/components/ThemeSettings.vue'
import UserMenu from '@/sail/components/UserMenu.vue'
import WorkspaceTabs from '@/sail/components/WorkspaceTabs.vue'
import { navigation } from '@/sail/navigation'
import { useMessageFeed } from '@/sail/use-message-feed'
import { useWorkspaceTabs } from '@/sail/use-workspace-tabs'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionaries'
import { useWebsiteStore } from '@/stores/website'

const route = useRoute()
const auth = useAuthStore()
const dictionaries = useDictionaryStore()
const website = useWebsiteStore()
/** 共享展示资料随配置修改同步更新，避免每个表格独立请求同一份字典。 */
function reloadDictionaries() {
  void dictionaries.load()
}
function reloadWebsite() {
  void website.load()
}
onMounted(() => {
  reloadDictionaries()
  reloadWebsite()
  window.addEventListener('sail:dictionaries-changed', reloadDictionaries)
  window.addEventListener('sail:website-changed', reloadWebsite)
})
onBeforeUnmount(() => {
  window.removeEventListener('sail:dictionaries-changed', reloadDictionaries)
  window.removeEventListener('sail:website-changed', reloadWebsite)
})
watch([() => website.title, () => route.meta.title], () => {
  document.title = `${String(route.meta.title ?? '工作台')} · ${website.title || '站点未配置'}`
})
const workspace = useWorkspaceTabs()
const { unread, connection } = useMessageFeed()
const fullscreen = ref(false)
const items = computed(() => navigation.filter(item => (!('admin' in item) || !item.admin || auth.isAdmin) && auth.can('permission' in item ? item.permission : undefined)))
const currentGroup = computed(() => items.value.find(item => item.path === route.path)?.group ?? '个人中心')
/** 工作区使用系统内专注布局，不申请浏览器全屏权限。 */
function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
}
/** Esc 在没有浮层时退出专注布局。 */
function exitFocus(event: KeyboardEvent) {
  if (event.key === 'Escape' && !document.querySelector('[role="dialog"], [data-slot="popover-content"], .sail-list-fullscreen'))
    fullscreen.value = false
}
document.addEventListener('keydown', exitFocus)
onBeforeUnmount(() => document.removeEventListener('keydown', exitFocus))
</script>

<template>
  <SidebarProvider class="sail-shell" :data-focus="fullscreen" style="--sidebar-width: calc(14rem - 20px)">
    <MessageAnnouncements />
    <AppSidebar />
    <SidebarInset class="min-w-0 bg-(--workspace-background)">
      <div class="sticky top-0 z-30 bg-background">
        <header class="flex h-12 shrink-0 items-center gap-2 border-b bg-background px-3 md:px-5">
          <SidebarTrigger aria-label="展开或收起侧栏" />
          <div class="mx-1 h-4 w-px bg-border" />
          <nav aria-label="面包屑" class="flex min-w-0 items-center gap-2 text-sm">
            <RouterLink to="/dashboard" class="hidden text-muted-foreground sm:inline">
              {{ currentGroup }}
            </RouterLink><ChevronRight class="hidden size-3 text-muted-foreground sm:inline" /><span class="truncate font-medium">{{ route.meta.title ?? '工作台' }}</span>
          </nav>
          <div class="ml-auto flex items-center gap-1">
            <Button variant="ghost" size="icon" class="size-8" aria-label="刷新当前页面" @click="workspace.refresh()">
              <RefreshCw class="size-4" />
            </Button>
            <Button variant="ghost" size="icon" class="hidden size-8 sm:inline-flex" :aria-label="fullscreen ? '退出全屏' : '全屏显示'" @click="toggleFullscreen">
              <component :is="fullscreen ? Minimize : Maximize" class="size-4" />
            </Button>
            <ThemeSettings />
            <MessageBell :unread="unread" />
            <div class="ml-2 border-l pl-3">
              <UserMenu :unread="unread" :connection="connection" />
            </div>
          </div>
        </header>
        <WorkspaceTabs :tabs="workspace.tabs.value" @close="workspace.close" @refresh="workspace.refresh" @group="workspace.closeGroup" />
      </div>
      <main class="min-w-0 flex-1 p-3 md:p-5">
        <RouterView v-slot="{ Component, route: pageRoute }">
          <KeepAlive :include="workspace.cacheNames.value">
            <component :is="workspace.cachedView(pageRoute.path)" v-if="Component" :key="workspace.cacheName(pageRoute.path)" :view="Component" />
          </KeepAlive>
        </RouterView>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>

<style>
.sail-shell[data-focus='true'] > [data-slot='sidebar'] {
  display: none;
}
.sail-shell[data-focus='true'] > [data-slot='sidebar-inset'] {
  width: 100%;
}

@media (prefers-reduced-motion: reduce) {
  .sail-shell [data-slot='sidebar'] *,
  .sail-shell [data-slot='sidebar'] {
    transition-duration: 0s;
  }
}
</style>
