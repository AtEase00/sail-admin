<script setup lang="ts">
import { Maximize, Minimize, RefreshCw, Search } from '@lucide/vue'
import { useScrollLock } from '@vueuse/core'
import { nextTick, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'

import type { ListColumn, TableDensity } from '@/sail/list-models'

import { Button } from '@/components/ui/button'

import ColumnSettings from './ColumnSettings.vue'

withDefaults(defineProps<{ title: string, total: number, loading?: boolean, columns?: ListColumn[] }>(), { columns: () => [] })
const emit = defineEmits<{ refresh: [] }>()
const showQuery = defineModel<boolean>('showQuery', { default: true })
const hiddenColumns = defineModel<string[]>('hiddenColumns', { default: () => [] })
const columnOrder = defineModel<string[]>('columnOrder', { default: () => [] })
const leftPins = defineModel<string[]>('leftPins', { default: () => [] })
const rightPins = defineModel<string[]>('rightPins', { default: () => [] })
const density = defineModel<TableDensity>('density', { default: 'compact' })
const panel = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const scrollLocked = useScrollLock(document.body)
let resizeObserver: ResizeObserver | undefined
let mutationObserver: MutationObserver | undefined
/** 使用真实列宽累加偏移，固定列随显隐、排序与视口同步。 */
async function syncPins() {
  await nextTick()
  const table = panel.value?.querySelector('table')
  if (!table)
    return
  const headers = Array.from(table.querySelectorAll<HTMLElement>('thead [data-column]'))
  const offsets = new Map<string, { side: string, offset: number }>()
  let leftOffset = 0
  let rightOffset = 0
  for (const header of headers) {
    const key = header.dataset.column!
    if (leftPins.value.includes(key)) {
      offsets.set(key, { side: 'left', offset: leftOffset })
      leftOffset += header.getBoundingClientRect().width
    }
  }
  for (const header of [...headers].reverse()) {
    const key = header.dataset.column!
    if (!leftPins.value.includes(key) && rightPins.value.includes(key)) {
      offsets.set(key, { side: 'right', offset: rightOffset })
      rightOffset += header.getBoundingClientRect().width
    }
  }
  for (const cell of table.querySelectorAll<HTMLElement>('[data-column]')) {
    const pin = offsets.get(cell.dataset.column!)
    cell.removeAttribute('data-pinned')
    cell.style.removeProperty('left')
    cell.style.removeProperty('right')
    if (pin) {
      cell.dataset.pinned = pin.side
      cell.style.setProperty(pin.side, `${pin.offset}px`)
    }
  }
}
/** 系统内全屏保留浏览器窗口；Esc 可随时恢复列表。 */
function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && !document.querySelector('[role="dialog"], [data-slot="popover-content"]'))
    fullscreen.value = false
}
watch(fullscreen, (value) => {
  scrollLocked.value = value
})
watch([leftPins, rightPins, columnOrder, hiddenColumns], syncPins, { deep: true })
onMounted(() => {
  resizeObserver = new ResizeObserver(syncPins)
  resizeObserver.observe(panel.value!)
  mutationObserver = new MutationObserver(syncPins)
  mutationObserver.observe(panel.value!, { childList: true, subtree: true })
  document.addEventListener('keydown', onKey)
  void syncPins()
})
onDeactivated(() => {
  fullscreen.value = false
})
onBeforeUnmount(() => {
  fullscreen.value = false
  scrollLocked.value = false
  resizeObserver?.disconnect()
  mutationObserver?.disconnect()
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <section ref="panel" :aria-label="title" class="sail-list min-w-0 space-y-3" :class="{ 'sail-list-fullscreen': fullscreen }" :data-density="density">
    <div v-if="showQuery && $slots.query">
      <slot name="query" />
    </div>
    <div class="rounded-lg border bg-background p-4">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-1 flex-wrap items-center justify-between gap-2">
          <div class="flex flex-wrap items-center gap-2">
            <slot name="actions" />
          </div><div class="ml-auto flex items-center gap-1">
            <Button v-if="$slots.query" variant="outline" size="icon" class="size-8 rounded-full" aria-label="显示或隐藏查询条件" :aria-pressed="showQuery" title="查询条件" @click="showQuery = !showQuery">
              <Search class="size-3.5" />
            </Button>
            <Button variant="outline" size="icon" class="size-8 rounded-full" aria-label="刷新列表" title="刷新列表" :disabled="loading" @click="emit('refresh')">
              <RefreshCw class="size-3.5" :class="{ 'animate-spin': loading }" />
            </Button>
            <Button variant="outline" size="icon" class="size-8 rounded-full" :aria-label="fullscreen ? '退出列表全屏' : '列表全屏'" :aria-pressed="fullscreen" title="系统内全屏" @click="fullscreen = !fullscreen">
              <component :is="fullscreen ? Minimize : Maximize" class="size-3.5" />
            </Button>
            <ColumnSettings v-if="columns.length" v-model:hidden="hiddenColumns" v-model:order="columnOrder" v-model:left="leftPins" v-model:right="rightPins" v-model:density="density" :columns="columns" />
          </div>
        </div>
      </div>
      <slot name="before" />
      <div class="overflow-hidden rounded-md border">
        <slot />
      </div>
      <slot name="pagination" />
    </div>
  </section>
</template>

<style>
.sail-list-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 40;
  overflow: auto;
  padding: 16px;
  background: var(--muted);
}
.sail-list [data-slot='table-head'] {
  height: 38px;
  padding: 8px 12px;
  background: var(--muted);
  text-align: center;
  font-size: 12px;
  white-space: nowrap;
}
.sail-list [data-slot='table-cell'] {
  padding: 8px 12px;
  text-align: center;
  font-size: 13px;
}
.sail-list[data-density='normal'] [data-slot='table-cell'] {
  padding-top: 12px;
  padding-bottom: 12px;
}
.sail-list[data-density='loose'] [data-slot='table-cell'] {
  padding-top: 18px;
  padding-bottom: 18px;
}
.sail-list [data-column] {
  min-width: 110px;
}
.sail-list [data-pinned] {
  position: sticky;
  z-index: 1;
  background: var(--background);
  box-shadow: 0 0 0 1px var(--border);
}
.sail-list th[data-pinned] {
  z-index: 2;
  background: var(--muted);
}
.sail-list tr:hover td[data-pinned] {
  background: var(--accent);
}
.sail-list .sail-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;
}
.sail-list .sail-row-actions button {
  height: 28px;
  padding-inline: 7px;
  font-size: 12px;
}
.sail-list .sail-row-actions button:not(.text-destructive) {
  color: var(--primary-text);
}
</style>
