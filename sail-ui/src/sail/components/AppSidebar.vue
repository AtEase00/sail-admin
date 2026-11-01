<script setup lang="ts">
import { ChevronDown, Command } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Sidebar, SidebarContent, SidebarGroup, SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarRail, useSidebar } from '@/components/ui/sidebar'
import { navigation } from '@/sail/navigation'
import { resolveMenuIcon } from '@/sail/navigation-icons'
import { useAuthStore } from '@/stores/auth'
import { useWebsiteStore } from '@/stores/website'

const auth = useAuthStore()
const website = useWebsiteStore()
const route = useRoute()
const { setOpenMobile, state, isMobile } = useSidebar()
const collapsed = computed(() => state.value === 'collapsed' && !isMobile.value)
const expandedGroup = ref<string | null>(null)
const hoveredGroup = ref<string | null>(null)
const openedByHover = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined
const groupPaths: Record<string, string> = { 工作空间: '/workspace', 系统管理: '/system', 文件管理: '/content', 消息管理: '/messages/manage', 支付管理: '/pay' }
const items = computed(() => navigation.filter(item => (!('admin' in item) || !item.admin || auth.isAdmin) && auth.can('permission' in item ? item.permission : undefined)))
const groups = computed(() => [...new Set(items.value.map(item => item.group))].filter(group => group !== '工作空间'))
/** 分组名称与图标读取后端目录，路径只用于对应固定页面能力。 */
function groupMenu(group: string) {
  return auth.menus.find(menu => menu.type === 'DIRECTORY' && menu.path === groupPaths[group])
}
/** 菜单显示保持固定尺寸，折叠只改变可见性，不改变悬停和选中布局。 */
function toggleGroup(group: string) {
  expandedGroup.value = expandedGroup.value === group ? null : group
}
/** 悬停面板允许鼠标跨过图标与浮层间隙，键盘点击同样可以展开。 */
function showGroup(group: string) {
  clearTimeout(closeTimer)
  if (collapsed.value) {
    openedByHover.value = true
    hoveredGroup.value = group
  }
}
/** 点击或键盘打开时允许浮层自动聚焦，悬停打开则保留原焦点。 */
function setGroupOpen(group: string, open: boolean) {
  openedByHover.value = false
  hoveredGroup.value = open ? group : null
}
/** 仅在鼠标离开浮层和入口后延迟关闭，避免移动到子菜单时闪退。 */
function hideGroup() {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    hoveredGroup.value = null
  }, 160)
}
/** 当前页所在目录在收起状态下继续标识，子项只在浮层出现。 */
function groupActive(group: string) {
  return items.value.some(item => item.group === group && item.path === route.path.replace(/\/$/, ''))
}
watch(() => route.fullPath, () => {
  setOpenMobile(false)
  hoveredGroup.value = null
})
watch(collapsed, () => {
  hoveredGroup.value = null
})
onBeforeUnmount(() => clearTimeout(closeTimer))
</script>

<template>
  <Sidebar collapsible="icon" class="sail-navigation">
    <SidebarHeader class="h-12 justify-center border-b px-3">
      <RouterLink to="/dashboard" class="flex items-center gap-2.5 overflow-hidden font-semibold">
        <img v-if="website.logo" :src="website.logo" class="size-6 shrink-0 object-contain" alt="站点标志"><Command v-else class="size-6 shrink-0" /><span class="truncate group-data-[collapsible=icon]:hidden">{{ website.title || (website.error ? '站点信息不可用' : '站点未配置') }}</span>
      </RouterLink>
    </SidebarHeader>
    <SidebarContent class="gap-0 py-2">
      <SidebarGroup class="py-0">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton as-child :is-active="route.path === '/dashboard'" tooltip="工作台" class="h-9">
              <RouterLink to="/dashboard">
                <component :is="resolveMenuIcon(auth.menus.find(menu => menu.path === '/dashboard')?.icon)" v-if="resolveMenuIcon(auth.menus.find(menu => menu.path === '/dashboard')?.icon)" class="size-4" /><span>工作台</span>
              </RouterLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
      <SidebarGroup v-for="group in groups" :key="group" class="py-1">
        <SidebarMenu>
          <SidebarMenuItem>
            <Popover :open="collapsed && hoveredGroup === group" @update:open="setGroupOpen(group, $event)">
              <PopoverTrigger as-child>
                <SidebarMenuButton class="h-10 font-medium" :is-active="groupActive(group) && (collapsed || expandedGroup !== group)" :aria-label="groupMenu(group)?.name ?? group" :aria-expanded="collapsed ? hoveredGroup === group : expandedGroup === group" @pointerenter="showGroup(group)" @pointerleave="hideGroup" @click="!collapsed && toggleGroup(group)">
                  <component :is="resolveMenuIcon(groupMenu(group)?.icon)" v-if="resolveMenuIcon(groupMenu(group)?.icon)" class="size-4 shrink-0" /><span>{{ groupMenu(group)?.name ?? group }}</span><ChevronDown class="ml-auto size-3.5 shrink-0 text-muted-foreground transition-transform group-data-[collapsible=icon]:hidden" :class="{ '-rotate-90': expandedGroup !== group }" />
                </SidebarMenuButton>
              </PopoverTrigger>
              <PopoverContent v-if="collapsed" side="right" align="start" :side-offset="8" class="sail-sidebar-popup w-52 p-1.5" @pointerenter="showGroup(group)" @pointerleave="hideGroup" @open-auto-focus="openedByHover && $event.preventDefault()" @close-auto-focus="openedByHover && $event.preventDefault()">
                <p class="px-2 py-2 text-xs font-medium text-muted-foreground">
                  {{ groupMenu(group)?.name ?? group }}
                </p>
                <nav class="max-h-[70dvh] space-y-1 overflow-y-auto" :aria-label="`${groupMenu(group)?.name ?? group}子菜单`">
                  <RouterLink v-for="item in items.filter(item => item.group === group)" :key="item.path" :to="item.path" class="sail-sidebar-link flex h-9 items-center gap-2 rounded px-2 text-sm" :aria-current="route.path === item.path ? 'page' : undefined" @click="hoveredGroup = null">
                    <component :is="resolveMenuIcon(auth.menus.find(menu => menu.path === item.path)?.icon)" v-if="resolveMenuIcon(auth.menus.find(menu => menu.path === item.path)?.icon)" class="size-4 shrink-0" />
                    <span>{{ auth.menus.find(menu => menu.path === item.path)?.name ?? item.title }}</span>
                  </RouterLink>
                </nav>
              </PopoverContent>
            </Popover>
          </SidebarMenuItem>
        </SidebarMenu>
        <SidebarMenu v-if="!collapsed && expandedGroup === group" class="sail-submenu mt-1 ml-4 w-auto border-l pl-2">
          <SidebarMenuItem v-for="item in items.filter(item => item.group === group)" :key="item.path">
            <SidebarMenuButton as-child :is-active="route.path.replace(/\/$/, '') === item.path" :tooltip="auth.menus.find(menu => menu.path === item.path)?.name ?? item.title" class="h-9">
              <RouterLink :to="item.path">
                <component :is="resolveMenuIcon(auth.menus.find(menu => menu.path === item.path)?.icon)" v-if="resolveMenuIcon(auth.menus.find(menu => menu.path === item.path)?.icon)" class="size-4" /><span>{{ auth.menus.find(menu => menu.path === item.path)?.name ?? item.title }}</span>
              </RouterLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
    </SidebarContent><SidebarRail />
  </Sidebar>
</template>

<style>
/* 保留按钮原有长宽，只调整层级和颜色，选中与悬停不引发布局变化。 */
.sail-navigation [data-sidebar='menu-button'][data-active='true'],
.sail-navigation [data-sidebar='menu-button'][data-active='true']:hover,
.sail-sidebar-link[aria-current='page'],
.sail-sidebar-link[aria-current='page']:hover {
  background: var(--primary);
  color: white;
  border-color: transparent;
  box-shadow: none;
}
.sail-navigation [data-sidebar='menu-button'][data-active='true'] svg {
  color: white;
}
.sail-sidebar-link:hover {
  background: var(--sidebar-accent);
  color: var(--sidebar-accent-foreground);
}
.sail-submenu {
  border-color: var(--sidebar-border);
}
</style>
