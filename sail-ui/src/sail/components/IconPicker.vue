<script setup lang="ts">
import { ChevronDown, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { menuIcons, resolveMenuIcon } from '@/sail/navigation-icons'

defineProps<{ id?: string, disabled?: boolean }>()
const value = defineModel<string>({ default: '' })
const open = ref(false)
const search = ref('')
const category = ref('全部')
const page = ref(1)
const categories = ['全部', ...new Set(menuIcons.map(icon => icon.category))]
const filtered = computed(() => menuIcons.filter(icon => (category.value === '全部' || icon.category === category.value) && `${icon.label} ${icon.value}`.includes(search.value.trim().toLowerCase())))
const visible = computed(() => filtered.value.slice((page.value - 1) * 60, page.value * 60))
watch([search, category], () => {
  page.value = 1
})
/** 选择稳定名称供后台存储，点击后关闭浮层。 */
function select(icon: string) {
  value.value = icon
  open.value = false
}
</script>

<template>
  <div class="flex items-center gap-2">
    <Popover v-model:open="open">
      <PopoverTrigger as-child>
        <Button :id="id" variant="outline" class="h-8 flex-1 justify-start font-normal" :disabled="disabled">
          <component :is="resolveMenuIcon(value)" v-if="resolveMenuIcon(value)" class="size-4" /><span class="flex-1 text-left">{{ value || '选择图标' }}</span><ChevronDown class="size-3.5 text-muted-foreground" />
        </Button>
      </PopoverTrigger><PopoverContent class="w-[min(420px,94vw)] p-3" align="start">
        <Input v-model="search" placeholder="搜索中文或英文图标名称" aria-label="搜索图标" class="mb-3 h-8" /><div class="mb-2 flex flex-wrap gap-1">
          <button v-for="item in categories" :key="item" type="button" class="rounded px-2 py-1 text-xs" :class="category === item ? 'bg-primary/10 text-primary' : 'hover:bg-muted text-muted-foreground'" @click="category = item">
            {{ item }}
          </button>
        </div><div class="grid max-h-72 grid-cols-6 gap-1 overflow-y-auto">
          <button v-for="icon in visible" :key="icon.value" type="button" class="flex h-12 items-center justify-center rounded-md hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" :class="value === icon.value ? 'bg-primary/10 text-primary' : ''" :aria-label="icon.label" :title="`${icon.label} · ${icon.value}`" :aria-pressed="value === icon.value" @click="select(icon.value)">
            <component :is="icon.component" class="size-5" />
          </button>
        </div><p v-if="!filtered.length" class="py-4 text-center text-sm text-muted-foreground">
          没有匹配的图标
        </p>
        <div class="mt-2 flex items-center justify-between border-t pt-2 text-xs text-muted-foreground">
          <span>共 {{ filtered.length }} 个图标</span><div class="flex items-center gap-2">
            <Button type="button" variant="ghost" size="sm" :disabled="page <= 1" @click="page--">
              上一页
            </Button><span>{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 60)) }}</span><Button type="button" variant="ghost" size="sm" :disabled="page * 60 >= filtered.length" @click="page++">
              下一页
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
    <Button v-if="value" type="button" variant="ghost" size="icon" class="size-8" :disabled="disabled" aria-label="清除图标" @click="value = ''">
      <X class="size-3.5" />
    </Button>
  </div>
</template>
