<script setup lang="ts">
import { ChevronDown, RotateCcw, Search } from '@lucide/vue'
import { ref } from 'vue'

import type { QueryField } from '@/sail/list-models'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

import DateRangePicker from './DateRangePicker.vue'
import FormSelect from './FormSelect.vue'

withDefaults(defineProps<{ keyword: string, status: string, createdFrom?: string, createdTo?: string, dates?: boolean, placeholder?: string, label?: string, statuses?: { value: string, label: string }[], busy?: boolean, scope?: string, fields?: QueryField[] }>(), { label: '关键词', placeholder: '请输入关键词', fields: () => [] })
const emit = defineEmits<{ 'update:createdFrom': [value: string], 'update:createdTo': [value: string], 'update:keyword': [value: string], 'update:status': [value: string], 'search': [], 'reset': [] }>()
const values = defineModel<Record<string, string>>('values', { default: () => ({}) })
const expanded = ref(true)
</script>

<template>
  <form class="rounded-lg border bg-background px-4 py-4" role="search" @submit.prevent="emit('search')">
    <div class="grid grid-cols-1 gap-x-4 gap-y-4 md:grid-cols-2 xl:grid-cols-4">
      <label class="flex min-w-0 items-center gap-2"><span class="w-14 shrink-0 text-right text-xs">{{ label }}</span><Input :model-value="keyword" :placeholder="placeholder" class="h-8 min-w-0 text-xs" @update:model-value="emit('update:keyword', String($event))" /></label>
      <template v-for="field in fields.slice(0, 1)" :key="field.key">
        <label class="flex min-w-0 items-center gap-2"><span class="w-14 shrink-0 text-right text-xs">{{ field.label }}</span><FormSelect v-if="field.options" v-model="values[field.key]" :options="field.options" :placeholder="`全部${field.label}`" /><Input v-else v-model="values[field.key]" :type="field.numeric ? 'number' : 'text'" :min="field.numeric ? 1 : undefined" :placeholder="field.placeholder ?? `请输入${field.label}`" class="h-8 min-w-0 text-xs" /></label>
      </template>
      <label v-if="statuses?.length" class="flex min-w-0 items-center gap-2"><span class="w-14 shrink-0 text-right text-xs">状态</span><FormSelect :model-value="status" :options="statuses" placeholder="全部状态" @update:model-value="emit('update:status', $event)" /></label>
      <template v-for="field in expanded ? fields.slice(1) : []" :key="field.key">
        <label class="flex min-w-0 items-center gap-2"><span class="w-14 shrink-0 text-right text-xs">{{ field.label }}</span><FormSelect v-if="field.options" v-model="values[field.key]" :options="field.options" :placeholder="`全部${field.label}`" /><Input v-else v-model="values[field.key]" :type="field.numeric ? 'number' : 'text'" :min="field.numeric ? 1 : undefined" :placeholder="field.placeholder ?? `请输入${field.label}`" class="h-8 min-w-0 text-xs" /></label>
      </template>
      <div v-if="dates && expanded" class="flex min-w-0 items-center gap-2 ">
        <!-- 创建时间为时间戳字段，查询固定使用带时分秒的日期时间范围。 -->
        <span class="w-14 shrink-0 text-right text-xs">创建时间</span><DateRangePicker mode="datetime" :from="createdFrom" :to="createdTo" @update:from="emit('update:createdFrom', $event)" @update:to="emit('update:createdTo', $event)" />
      </div>
      <div class="flex flex-wrap items-center justify-end gap-2  xl:col-start-4">
        <span v-if="scope" class="mr-auto text-xs text-muted-foreground">{{ scope }}</span><Button type="button" variant="outline" class="h-8 px-3 text-xs" :disabled="busy" @click="emit('reset')">
          <RotateCcw class="size-3.5" />重置
        </Button><Button type="submit" class="h-8 px-3 text-xs" :disabled="busy">
          <Search class="size-3.5" />查询
        </Button><Button v-if="dates || fields.length > 1" type="button" variant="ghost" class="h-8 px-2 text-xs" :aria-expanded="expanded" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}<ChevronDown class="size-3.5" :class="{ 'rotate-180': expanded }" />
        </Button>
      </div>
    </div>
  </form>
</template>
