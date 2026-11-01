<script setup lang="ts">
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from '@lucide/vue'
import { computed, ref } from 'vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

import FormSelect from './FormSelect.vue'

const props = defineProps<{ page: number, size: number, total: number, busy?: boolean }>()
const emit = defineEmits<{ 'change': [page: number], 'update:size': [size: number] }>()
const last = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const jump = ref('')
const numbers = computed(() => {
  const start = Math.max(1, Math.min(props.page - 2, last.value - 4))
  return Array.from({ length: Math.min(5, last.value) }, (_, index) => start + index)
})
/** 跳页只接受现有页码范围内的整数。 */
function go() {
  const target = Number(jump.value)
  if (Number.isInteger(target) && target >= 1 && target <= last.value) {
    emit('change', target)
    jump.value = ''
  }
}
/** 改变每页条数同时回到第一页，避免页码越界。 */
function resize(value: string) {
  emit('update:size', Number(value))
  emit('change', 1)
}
</script>

<template>
  <nav aria-label="列表分页" class="flex flex-wrap items-center justify-between gap-3 pt-4 text-xs">
    <div class="flex items-center gap-3 text-muted-foreground">
      <span>共 {{ total }} 条</span><label class="flex items-center gap-2"><span class="sr-only">每页条数</span><span class="w-28"><FormSelect :model-value="String(size)" :disabled="busy" aria-label="每页条数" required :options="[10, 20, 50, 100, 200].map(count => ({ value: String(count), label: `${count} 条/页` }))" @update:model-value="resize" /></span></label>
    </div>
    <div class="flex flex-wrap items-center gap-1">
      <Button variant="ghost" size="icon" class="size-8" aria-label="第一页" :disabled="busy || page <= 1" @click="emit('change', 1)">
        <ChevronsLeft class="size-4" />
      </Button>
      <Button variant="ghost" size="icon" class="size-8" aria-label="上一页" :disabled="busy || page <= 1" @click="emit('change', page - 1)">
        <ChevronLeft class="size-4" />
      </Button>
      <span v-if="numbers[0] > 1" class="px-1">…</span>
      <Button v-for="number in numbers" :key="number" :variant="page === number ? 'default' : 'ghost'" size="icon" class="size-8 text-xs" :aria-label="`第 ${number} 页`" :aria-current="page === number ? 'page' : undefined" :disabled="busy" @click="emit('change', number)">
        {{ number }}
      </Button>
      <span v-if="numbers[numbers.length - 1] < last" class="px-1">…</span>
      <Button variant="ghost" size="icon" class="size-8" aria-label="下一页" :disabled="busy || page >= last" @click="emit('change', page + 1)">
        <ChevronRight class="size-4" />
      </Button>
      <Button variant="ghost" size="icon" class="size-8" aria-label="最后一页" :disabled="busy || page >= last" @click="emit('change', last)">
        <ChevronsRight class="size-4" />
      </Button>
      <form class="ml-2 flex items-center gap-2" @submit.prevent="go">
        <span>前往</span><Input v-model="jump" type="number" :min="1" :max="last" :disabled="busy" aria-label="跳转页码" class="h-8 w-14 px-2 text-center text-xs" /><span>/ {{ last }} 页</span><Button type="submit" size="sm" variant="outline" class="h-8 px-2 text-xs" :disabled="busy || !jump">
          跳转
        </Button>
      </form>
    </div>
  </nav>
</template>
