<script setup lang="ts">
import { computed } from 'vue'

import type { TreeItem } from '@/sail/tree'

import { Checkbox } from '@/components/ui/checkbox'
import { branchIds } from '@/sail/tree'

const props = defineProps<{ node: TreeItem, selected: number[], disabled: boolean }>()
const emit = defineEmits<{ toggle: [ids: number[], checked: boolean] }>()
const ids = computed(() => branchIds(props.node))
const state = computed(() => {
  const count = ids.value.filter(id => props.selected.includes(id)).length
  return count === ids.value.length && count > 0 ? true : count > 0 ? 'indeterminate' : false
})
</script>

<template>
  <div>
    <label class="flex min-h-10 flex-wrap items-center gap-2 rounded px-2 py-2 hover:bg-muted/60">
      <Checkbox :model-value="state" :disabled="disabled" @update:model-value="emit('toggle', ids, $event === true)" />
      <span class="text-sm" :class="{ 'font-medium': node.children.length }">{{ node.label }}</span>
      <span v-if="node.row?.permission && node.row.permission !== node.label" class="ml-auto break-all text-xs text-muted-foreground">{{ node.row.permission }}</span>
      <span v-if="!node.row" class="ml-auto break-all text-xs text-muted-foreground">{{ ids.length }} 项</span>
      <span v-if="node.row?.status === 0" class="text-xs text-muted-foreground">已停用</span>
    </label>
    <div v-if="node.children.length" class="ml-2 border-l pl-2 sm:ml-4 sm:pl-3">
      <PermissionBranch v-for="child in node.children" :key="child.key" :node="child" :selected="selected" :disabled="disabled" @toggle="(ids, checked) => emit('toggle', ids, checked)" />
    </div>
  </div>
</template>
