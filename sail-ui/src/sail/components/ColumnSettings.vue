<script setup lang="ts">
import { ArrowDown, ArrowUp, GripVertical, Pin, Settings2 } from '@lucide/vue'
import { computed, ref } from 'vue'

import type { ListColumn, TableDensity } from '@/sail/list-models'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

import FormSelect from './FormSelect.vue'

const props = defineProps<{ columns: ListColumn[] }>()
const hidden = defineModel<string[]>('hidden', { required: true })
const order = defineModel<string[]>('order', { required: true })
const left = defineModel<string[]>('left', { required: true })
const right = defineModel<string[]>('right', { required: true })
const density = defineModel<TableDensity>('density', { required: true })
const dragging = ref('')
const ordered = computed(() => [...new Set([...order.value, ...props.columns.map(column => column.key)])].map(key => props.columns.find(column => column.key === key)).filter((column): column is ListColumn => Boolean(column)))
const allShown = computed(() => ordered.value.every(column => !hidden.value.includes(column.key)))
/** 显隐保留首个业务列，防止空表失去对象标识。 */
function toggle(key: string, shown: boolean) {
  hidden.value = shown ? hidden.value.filter(item => item !== key) : [...new Set([...hidden.value, key])]
}
/** 列左右固定互斥，重复点击取消固定。 */
function pin(key: string, side: 'left' | 'right') {
  const active = (side === 'left' ? left : right).value.includes(key)
  const nextLeft = left.value.filter(item => item !== key)
  const nextRight = right.value.filter(item => item !== key)
  if (!active)
    (side === 'left' ? nextLeft : nextRight).push(key)
  left.value = nextLeft
  right.value = nextRight
}
/** 拖动和键盘按钮共用真实列顺序，不改变字段或数据。 */
function move(key: string, target: number) {
  const keys = ordered.value.map(column => column.key)
  const source = keys.indexOf(key)
  if (source < 0 || target < 0 || target >= keys.length)
    return
  keys.splice(source, 1)
  keys.splice(target, 0, key)
  order.value = keys
}
/** 一次还原当前表格全部展示设置。 */
function reset() {
  hidden.value = []
  order.value = []
  left.value = []
  right.value = []
  density.value = 'compact'
}
</script>

<template>
  <Popover>
    <PopoverTrigger as-child>
      <Button variant="outline" size="icon" class="size-8 rounded-full" aria-label="列表设置" title="列设置">
        <Settings2 class="size-3.5" />
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" class="w-80 p-0">
      <div class="flex items-center justify-between border-b p-3">
        <strong class="text-sm">表格设置</strong><Button variant="link" size="sm" class="h-6 px-0 text-xs" @click="reset">
          恢复默认
        </Button>
      </div>
      <div class="flex items-center justify-between border-b px-3 py-2 text-xs">
        <span>表格密度</span><div class="w-24">
          <FormSelect :model-value="density" aria-label="表格密度" required :options="[{ value: 'compact', label: '紧凑' }, { value: 'normal', label: '标准' }, { value: 'loose', label: '宽松' }]" @update:model-value="density = $event as TableDensity" />
        </div>
      </div>
      <template v-if="columns.length">
        <label class="flex items-center gap-2 border-b p-3 text-xs"><Checkbox :model-value="allShown ? true : hidden.length ? 'indeterminate' : false" @update:model-value="hidden = $event === true ? [] : columns.slice(1).map(column => column.key)" />全部列<span class="ml-auto text-muted-foreground">拖动排序 · 点击图钉固定</span></label>
        <div class="max-h-72 overflow-y-auto py-1">
          <div v-for="(column, index) in ordered" :key="column.key" class="flex items-center gap-1 px-2 py-1 hover:bg-muted/50" draggable="true" @dragstart="dragging = column.key" @dragover.prevent @drop.prevent="move(dragging, index)" @dragend="dragging = ''">
            <GripVertical class="size-3.5 shrink-0 cursor-grab text-muted-foreground" /><Checkbox :model-value="!hidden.includes(column.key) || column.key === columns[0]?.key" :disabled="column.key === columns[0]?.key" :aria-label="`显示${column.label}列`" @update:model-value="toggle(column.key, $event === true)" /><span class="min-w-0 flex-1 truncate pl-1 text-xs">{{ column.label }}</span>
            <Button size="icon" variant="ghost" class="size-6" :disabled="index === 0" :aria-label="`上移${column.label}列`" @click="move(column.key, index - 1)">
              <ArrowUp class="size-3" />
            </Button><Button size="icon" variant="ghost" class="size-6" :disabled="index === ordered.length - 1" :aria-label="`下移${column.label}列`" @click="move(column.key, index + 1)">
              <ArrowDown class="size-3" />
            </Button>
            <Button size="icon" variant="ghost" class="size-6" :aria-label="`左侧固定${column.label}列`" :aria-pressed="left.includes(column.key)" :class="{ 'bg-accent text-primary': left.includes(column.key) }" title="固定到左侧" @click="pin(column.key, 'left')">
              <Pin class="size-3 -rotate-45" />
            </Button><Button size="icon" variant="ghost" class="size-6" :aria-label="`右侧固定${column.label}列`" :aria-pressed="right.includes(column.key)" :class="{ 'bg-accent text-primary': right.includes(column.key) }" title="固定到右侧" @click="pin(column.key, 'right')">
              <Pin class="size-3 rotate-45" />
            </Button>
          </div>
        </div>
      </template>
    </PopoverContent>
  </Popover>
</template>
