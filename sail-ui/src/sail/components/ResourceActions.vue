<script setup lang="ts">
import { MoreHorizontal } from '@lucide/vue'
import { computed, onDeactivated, ref } from 'vue'

import type { Row } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import SailForm from '@/sail/components/SailForm.vue'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/sail/password-policy'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ resource: string, row: Row }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const open = ref(false)
const kickConfirm = ref(false)
const title = ref('')
const action = ref('')
const choices = ref<Row[]>([])
const choiceSearch = ref('')
const filteredChoices = computed(() => choices.value.filter(choice => `${choice.name} ${choice.code ?? ''}`.toLowerCase().includes(choiceSearch.value.trim().toLowerCase())))
const selected = ref<number[]>([])
const password = ref('')
const error = useMessageError()
const busy = ref(false)
const sessionRows = ref<Record<string, unknown>[]>([])
const endpoint = computed(() => `/system/${props.resource}/${props.row.id}`)
const loaded = ref(false)
/** 授权关系先获取当前值与真实目录，避免全量替换时误清空未加载的数据。 */
async function edit(kind: string, label: string) {
  action.value = kind
  title.value = label
  error.value = ''
  loaded.value = false
  open.value = true
  password.value = ''
  busy.value = true
  choiceSearch.value = ''
  choices.value = []
  selected.value = []
  sessionRows.value = []
  try {
    if (kind === 'sessions') {
      sessionRows.value = await request<Record<string, unknown>[]>(`${endpoint.value}/sessions`)
    }
    else if (kind !== 'password') {
      const [ids, list] = await Promise.all([
        request<number[]>(`${endpoint.value}/${kind}`),
        request<Row[]>(`/system/${kind}`),
      ])
      selected.value = ids
      choices.value = list
    }
    loaded.value = true
  }
  catch (failure) {
    error.value = errorMessage(failure, '加载失败')
  }
  finally {
    busy.value = false
  }
}
/** 仅更新用户明确勾选的关联编号。 */
function toggle(id: number, checked: boolean) {
  selected.value = checked
    ? [...new Set([...selected.value, id])]
    : selected.value.filter(item => item !== id)
}
/** 校验加载状态后提交明确的字段或授权集合。 */
async function save() {
// 加载失败不能全量覆盖授权；提交失败保留已加载内容以便重试。
  if (busy.value || !loaded.value)
    return
  busy.value = true
  error.value = ''
  try {
    await request(
      `${endpoint.value}/${action.value}`,
      'PUT',
      action.value === 'password' ? { password: password.value } : { ids: selected.value },
    )
    open.value = false
    feedback.success('操作已保存')
    emit('changed')
    await auth.hydrate(true)
  }
  catch (failure) {
    error.value = errorMessage(failure, '操作失败')
  }
  finally {
    busy.value = false
  }
}
/** 将实际选中的启用存储配置设为默认实例。 */
async function selectStorage() {
  busy.value = true
  try {
    await request(`/file/storages/${props.row.id}/default`, 'PUT')
    feedback.success('默认存储已切换')
    emit('changed')
  }
  catch (failure) {
    feedback.error(failure, '切换失败')
  }
  finally {
    busy.value = false
  }
}
/** 确认后撤销目标账号的全部会话。 */
async function kickout() {
  busy.value = true
  try {
    await request(`/system/audit/sessions/${props.row.id}`, 'DELETE')
    sessionRows.value = []
    feedback.success('账号已下线')
    kickConfirm.value = false
  }
  catch (failure) {
    error.value = errorMessage(failure, '下线失败')
  }
  finally {
    busy.value = false
  }
}
onDeactivated(() => {
  open.value = false
  kickConfirm.value = false
})
</script>

<template>
  <template v-if="auth.isAdmin && resource === 'users'">
    <Button size="sm" variant="ghost" :disabled="busy" @click="edit('roles', '分配角色')">
      角色
    </Button>
    <DropdownMenu>
      <DropdownMenuTrigger as-child>
        <Button variant="ghost" size="icon" class="size-8" aria-label="更多用户操作">
          <MoreHorizontal class="size-4" />
        </Button>
      </DropdownMenuTrigger><DropdownMenuContent align="end">
        <DropdownMenuItem @select="edit('posts', '分配岗位')">
          分配岗位
        </DropdownMenuItem><DropdownMenuItem @select="edit('password', '重置密码')">
          重置密码
        </DropdownMenuItem><DropdownMenuItem @select="edit('sessions', '登录会话')">
          登录会话
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </template>
  <template v-if="auth.isAdmin && resource === 'roles' && !row.builtIn">
    <Button
      v-if="row.dataScope === 2"
      size="sm"
      variant="ghost"
      :disabled="busy"
      @click="edit('depts', '分配自定义部门')"
    >
      数据范围
    </Button>
  </template>
  <Button
    v-if="auth.isAdmin && resource === 'storages' && !row.selected && row.enabled"
    size="sm"
    variant="ghost"
    :disabled="busy"
    @click="selectStorage"
  >
    设为默认
  </Button>
  <Dialog v-model:open="kickConfirm">
    <DialogContent>
      <DialogHeader><DialogTitle>撤销全部登录会话</DialogTitle><DialogDescription>确认让账号「{{ row.username }}」在所有设备上退出登录？</DialogDescription></DialogHeader><DialogFooter>
        <Button variant="outline" :disabled="busy" @click="kickConfirm = false">
          取消
        </Button><Button variant="destructive" :disabled="busy" @click="kickout">
          确认下线
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
  <Dialog v-model:open="open">
    <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription>{{ row.nickname ?? row.name ?? row.username }}<span v-if="row.username" class="ml-2">{{ row.username }}</span></DialogDescription>
      </DialogHeader>
      <SailForm class="space-y-4" @submit.prevent="save">
        <p v-if="busy" role="status">
          正在处理…
        </p>

        <div v-if="action === 'password'" class="space-y-2">
          <Label for="reset-password">新密码（至少 {{ PASSWORD_MIN_LENGTH }} 个字符）</Label>
          <Input
            id="reset-password"
            v-model="password"
            type="password"
            required
            :minlength="PASSWORD_MIN_LENGTH"
            :maxlength="PASSWORD_MAX_LENGTH"
            autocomplete="new-password"
          />
        </div>
        <template v-else-if="action === 'sessions'">
          <p v-if="!sessionRows.length">
            当前没有有效会话。
          </p>
          <div
            v-for="(session, index) in sessionRows"
            :key="index"
            class="rounded-md border p-3 text-sm"
          >
            <p v-for="(value, key) in session" :key="key">
              {{ ({ index: '终端编号', deviceType: '登录设备', createdAt: '登录时间' } as Record<string, string>)[key] ?? key }}：{{ value }}
            </p>
          </div>
          <Button type="button" variant="destructive" :disabled="busy" @click="kickConfirm = true">
            强制全部下线
          </Button>
        </template>
        <div v-else class="space-y-3">
          <Input v-model="choiceSearch" :placeholder="action === 'roles' ? '搜索角色名称或编码' : '搜索名称或编码'" aria-label="搜索可分配项" />
          <div class="flex items-center justify-between text-xs text-muted-foreground">
            <span>可分配 {{ choices.length }} 项</span><span>已选择 {{ selected.length }} 项</span>
          </div>
          <div class="max-h-80 divide-y overflow-y-auto rounded-md border">
            <label v-for="choice in filteredChoices" :key="choice.id" class="flex min-h-16 cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/40" :class="{ 'bg-primary/5': selected.includes(choice.id) }">
              <Checkbox :model-value="selected.includes(choice.id)" :disabled="busy || !loaded || (choice.status === 0 && !selected.includes(choice.id))" @update:model-value="toggle(choice.id, $event === true)" />
              <span class="min-w-0 flex-1"><span class="block truncate text-sm font-medium">{{ choice.name }}</span><span v-if="choice.code" class="block truncate text-xs text-muted-foreground">{{ choice.code }}</span></span>
              <span v-if="choice.status === 0" class="text-xs text-muted-foreground">停用</span><span v-else-if="choice.builtIn" class="text-xs text-muted-foreground">系统内置</span>
            </label>
            <p v-if="!filteredChoices.length && !busy && !error" class="p-8 text-center text-sm text-muted-foreground">
              {{ choices.length ? '没有匹配项' : '暂无可分配项' }}
            </p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" type="button" @click="open = false">
            关闭
          </Button>
          <Button v-if="action !== 'sessions'" type="submit" :disabled="busy || !loaded">
            保存
          </Button>
        </DialogFooter>
      </SailForm>
    </DialogContent>
  </Dialog>
</template>
