<script setup lang="ts">
import { ChevronDown, ChevronLeft, ChevronRight, Home, RefreshCw, X } from '@lucide/vue'
import { useResizeObserver } from '@vueuse/core'
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { Button } from '@/components/ui/button'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuTrigger } from '@/components/ui/context-menu'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

const props = defineProps<{ tabs: { path: string, title: string, pinned: boolean }[] }>()
const emit = defineEmits<{ close: [path: string], refresh: [path: string], group: [path: string, kind: 'others' | 'left' | 'right' | 'all'] }>()
const route = useRoute()
const strip = ref<HTMLElement | null>(null)
const canLeft = ref(false)
const canRight = ref(false)
/** 箭头仅在确实存在隐藏标签时出现，不占用标签行高度。 */
function measure() {
  const el = strip.value
  if (!el)
    return
  canLeft.value = el.scrollLeft > 1
  canRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
}
/** 每次移动约一屏，保留相邻标签作为位置参照。 */
function move(direction: number) {
  const el = strip.value
  if (el)
    el.scrollBy({ left: direction * el.clientWidth * 0.75, behavior: 'auto' })
}
/** 普通滚轮横向浏览标签，Ctrl 缩放和不溢出的滚动保持浏览器默认行为。 */
function wheel(event: WheelEvent) {
  if (event.ctrlKey || !strip.value || strip.value.scrollWidth <= strip.value.clientWidth)
    return
  event.preventDefault()
  strip.value.scrollLeft += event.deltaY || event.deltaX
}
useResizeObserver(strip, measure)
/** 活跃标签保持可见，使用即时滚动避免给页面切换增加动画。 */
async function reveal() {
  await nextTick()
  const el = strip.value
  const active = el?.querySelector<HTMLElement>('[aria-current="page"]')?.parentElement
  if (el && active) {
    // 只滚动标签容器，避免 scrollIntoView 连带改变整个页面位置。
    const offset = active.getBoundingClientRect().left - el.getBoundingClientRect().left
    if (offset < 0)
      el.scrollLeft += offset
    else if (offset + active.offsetWidth > el.clientWidth)
      el.scrollLeft += offset + active.offsetWidth - el.clientWidth
  }
  measure()
}
watch([() => route.path, () => props.tabs.length], reveal, { immediate: true })
</script>

<template>
  <div class="sail-workspace-tabs flex h-10 min-w-0 items-stretch bg-background" data-workspace-tabs>
    <Button v-if="canLeft || canRight" variant="ghost" size="icon" class="h-full w-7 shrink-0 rounded-none border-r" :disabled="!canLeft" aria-label="向左浏览标签" @click="move(-1)">
      <ChevronLeft class="size-3.5" />
    </Button>
    <nav ref="strip" aria-label="已打开页面" class="sail-tab-strip flex min-w-0 flex-1 items-stretch overflow-x-auto overflow-y-hidden" @scroll="measure" @wheel="wheel" @pointerover="measure" @pointerout="measure" @focusin="measure" @focusout="measure" @transitionend="measure">
      <ContextMenu v-for="tab in tabs" :key="tab.path">
        <ContextMenuTrigger as-child>
          <!-- 所有页签直角、等高、无间隙；宽度变化只在水平方向展开，不产生上下位移。 -->
          <div class="sail-workspace-tab group relative flex h-full shrink-0 items-center border-r border-b text-xs transition-colors duration-150 motion-reduce:transition-none" :data-active="route.path === tab.path" :class="route.path === tab.path ? 'border-b-transparent bg-accent font-medium text-primary' : 'border-b-border text-muted-foreground hover:bg-muted/60 hover:text-foreground'" @auxclick.middle.prevent="!tab.pinned && emit('close', tab.path)">
            <RouterLink :to="tab.path" :aria-current="route.path === tab.path ? 'page' : undefined" class="sail-workspace-tab-link flex h-full items-center gap-1.5 pr-3.5 pl-3.5 outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring" :class="{ 'sail-workspace-tab-link-closable': !tab.pinned }" @dblclick="!tab.pinned && emit('close', tab.path)">
              <Home v-if="tab.pinned" class="size-3.5" />{{ tab.title }}
            </RouterLink>
            <!-- 关闭按钮持续挂载，以宽度和透明度双向过渡；收起后不占横向空间。 -->
            <button v-if="!tab.pinned" class="sail-workspace-tab-close flex h-5 shrink-0 items-center justify-center text-muted-foreground outline-none hover:bg-foreground/10 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring" :aria-label="`关闭${tab.title}标签`" @click="emit('close', tab.path)">
              <X class="size-3 shrink-0" />
            </button>
          </div>
        </ContextMenuTrigger>
        <ContextMenuContent>
          <ContextMenuItem @select="emit('refresh', tab.path)">
            <RefreshCw class="size-3.5" />刷新页面
          </ContextMenuItem><ContextMenuItem :disabled="tab.pinned" @select="emit('close', tab.path)">
            关闭当前
          </ContextMenuItem><ContextMenuSeparator /><ContextMenuItem @select="emit('group', tab.path, 'others')">
            关闭其他
          </ContextMenuItem><ContextMenuItem @select="emit('group', tab.path, 'left')">
            关闭左侧
          </ContextMenuItem><ContextMenuItem @select="emit('group', tab.path, 'right')">
            关闭右侧
          </ContextMenuItem><ContextMenuItem @select="emit('group', tab.path, 'all')">
            关闭全部
          </ContextMenuItem>
        </ContextMenuContent>
      </ContextMenu>
    </nav>
    <Button v-if="canLeft || canRight" variant="ghost" size="icon" class="h-full w-7 shrink-0 rounded-none border-l" :disabled="!canRight" aria-label="向右浏览标签" @click="move(1)">
      <ChevronRight class="size-3.5" />
    </Button>
    <DropdownMenu>
      <DropdownMenuTrigger as-child>
        <Button variant="ghost" size="icon" class="h-full w-9 shrink-0 rounded-none border-l" aria-label="标签操作">
          <ChevronDown class="size-4" />
        </Button>
      </DropdownMenuTrigger><DropdownMenuContent align="end" class="max-h-[70vh] overflow-y-auto">
        <DropdownMenuItem @select="emit('refresh', route.path)">
          刷新当前页
        </DropdownMenuItem><DropdownMenuItem :disabled="route.path === '/dashboard'" @select="emit('close', route.path)">
          关闭当前标签
        </DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuItem @select="emit('group', route.path, 'others')">
          关闭其他标签
        </DropdownMenuItem><DropdownMenuItem @select="emit('group', route.path, 'all')">
          关闭全部标签
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </div>
</template>

<style scoped>
/* 空白区域保留细分隔线，当前页签覆盖底线；顶部和底部不再留出独立卡片间隙。 */
.sail-workspace-tabs {
  box-shadow: inset 0 -1px 0 var(--border);
}

/* 同步文字右侧留白和按钮占位，避免展开或收起时出现先跳动再过渡。 */
.sail-workspace-tab-link {
  transition: padding-right 180ms cubic-bezier(0.22, 1, 0.36, 1);
}
.sail-workspace-tab-close {
  width: 0;
  min-width: 0;
  margin-right: 0;
  padding: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transition:
    width 180ms cubic-bezier(0.22, 1, 0.36, 1),
    margin-right 180ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease-out;
}
.sail-workspace-tab:is([data-active='true'], :hover, :focus-within) .sail-workspace-tab-link-closable {
  padding-right: 6px;
}
.sail-workspace-tab:is([data-active='true'], :hover, :focus-within) .sail-workspace-tab-close {
  width: 20px;
  margin-right: 6px;
  opacity: 1;
  pointer-events: auto;
}
/* 尊重系统减少动态效果的偏好，保留相同展开状态和键盘操作。 */
@media (prefers-reduced-motion: reduce) {
  .sail-workspace-tab-link,
  .sail-workspace-tab-close {
    transition: none;
  }
}
</style>
