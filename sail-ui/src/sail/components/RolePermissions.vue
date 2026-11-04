<script setup lang="ts">
import { computed, onDeactivated, ref } from 'vue'

import type { Row } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import { buildTree, filterTree } from '@/sail/tree'
import { useAuthStore } from '@/stores/auth'

import PermissionBranch from './PermissionBranch.vue'

const props = defineProps<{ row: Row }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const opened = ref(false)
const busy = ref(false)
const loaded = ref(false)
const error = useMessageError()
const keyword = ref('')
const menus = ref<Row[]>([])
const selected = ref<number[]>([])
const tree = computed(() => filterTree(buildTree(menus.value, true), keyword.value))
/** 一次加载全部真实菜单及当前角色授权，内置超级管理员明确显示全部权限。 */
async function show() {
  opened.value = true
  busy.value = true
  loaded.value = false
  error.value = ''
  keyword.value = ''
  try {
    const [list, ids] = await Promise.all([request<Row[]>('/system/menus'), request<number[]>(`/system/roles/${props.row.id}/menus`)])
    menus.value = list
    selected.value = props.row.builtIn ? list.map(item => item.id) : ids
    loaded.value = true
  }
  catch (failure) {
    error.value = errorMessage(failure, '权限加载失败')
  }
  finally { busy.value = false }
}
/** 分支联动只操作实际菜单编号，未展示的已分配编号不会被搜索丢弃。 */
function toggle(ids: number[], checked: boolean) {
  selected.value = checked ? [...new Set([...selected.value, ...ids])] : selected.value.filter(id => !ids.includes(id))
}
/** 原子替换当前角色授权；保存后重新读取，确认回显与数据库一致。 */
async function save() {
  if (busy.value || !loaded.value || props.row.builtIn)
    return
  busy.value = true
  error.value = ''
  try {
    await request(`/system/roles/${props.row.id}/menus`, 'PUT', { ids: selected.value })
    selected.value = await request<number[]>(`/system/roles/${props.row.id}/menus`)
    await auth.hydrate(true)
    feedback.success('角色菜单权限已保存')
    emit('changed')
    opened.value = false
  }
  catch (failure) {
    error.value = errorMessage(failure, '授权保存失败')
  }
  finally { busy.value = false }
}
onDeactivated(() => {
  opened.value = false
})
</script>

<template>
  <Button size="sm" variant="ghost" class="text-primary" @click="show">
    {{ row.builtIn ? '查看权限' : '分配权限' }}
  </Button>
  <Dialog v-model:open="opened">
    <DialogContent :aria-describedby="undefined" class="flex max-h-[85svh] flex-col sm:max-w-2xl">
      <DialogHeader><DialogTitle>{{ row.name }} · 菜单权限</DialogTitle></DialogHeader>
      <div class="flex flex-wrap items-center gap-2">
        <Input v-model="keyword" class="min-w-40 flex-1" aria-label="搜索菜单权限" placeholder="搜索名称或权限标识" /><template v-if="!row.builtIn">
          <Button variant="outline" size="sm" :disabled="busy || !loaded" @click="selected = menus.map(item => item.id)">
            全选
          </Button><Button variant="outline" size="sm" :disabled="busy || !loaded" @click="selected = []">
            清空
          </Button>
        </template><span class="text-xs text-muted-foreground">已选 {{ selected.length }} 项</span>
      </div>

      <div class="min-h-40 overflow-y-auto rounded-md border p-2">
        <p v-if="busy" class="p-4 text-sm text-muted-foreground">
          正在读取权限…
        </p><template v-else>
          <PermissionBranch v-for="node in tree" :key="node.key" :node="node" :selected="selected" :disabled="Boolean(row.builtIn) || !auth.isAdmin" @toggle="toggle" /><p v-if="!tree.length" class="p-6 text-center text-sm text-muted-foreground">
            没有匹配的菜单权限
          </p>
        </template>
      </div>
      <DialogFooter>
        <Button v-if="error" variant="outline" :disabled="busy" @click="show">
          重试
        </Button>
        <Button variant="outline" @click="opened = false">
          取消
        </Button><Button v-if="!row.builtIn && auth.isAdmin" :disabled="busy || !loaded" @click="save">
          保存权限
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
