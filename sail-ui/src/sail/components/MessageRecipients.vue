<script setup lang="ts">
import { Search, Users, X } from '@lucide/vue'
import { Checkbox as ACheckbox, Input as AInput, Select as ASelect, Switch as ASwitch, Modal, TreeSelect } from 'ant-design-vue'
import { computed, reactive, ref, watch } from 'vue'

import type { AudienceOptions, MessageAudience, MessagePerson } from '@/sail/message-center'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { request } from '@/lib/api-client'
import { cloneAudience } from '@/sail/message-center'
import { useAuthStore } from '@/stores/auth'

import ListPagination from './ListPagination.vue'

const props = defineProps<{ disabled?: boolean }>()
const model = defineModel<MessageAudience>({ required: true })
interface DepartmentNode { value: number, title: string, children: DepartmentNode[] }
const auth = useAuthStore()
const open = ref(false)
const target = ref<'userIds' | 'excludedIds'>('userIds')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const applied = ref('')
const dept = ref<number>()
const loading = ref(false)
const error = ref('')
const rows = ref<MessagePerson[]>([])
const options = ref<AudienceOptions>({ departments: [], roles: [], posts: [] })
const people = reactive(new Map<number, MessagePerson>())
const local = ref<MessageAudience>(cloneAudience(model.value))
let revision = 0
const tree = computed(() => {
  // 部门树仅使用接口给出的真实父子关系，孤立节点以根节点保留。
  const map = new Map<number, DepartmentNode>(options.value.departments.map(item => [item.id, { value: item.id, title: item.name, children: [] }]))
  const roots: DepartmentNode[] = []
  options.value.departments.forEach((item) => {
    const node = map.get(item.id)!
    if (item.parentId && item.parentId !== item.id && map.has(item.parentId))
      map.get(item.parentId)!.children.push(node); else
      roots.push(node)
  })
  return roots
})
const summary = computed(() => {
  const value = model.value
  const names: string[] = []
  if (value.all)
    names.push('全体用户')
  if (value.userIds.length)
    names.push(`${value.userIds.length} 名指定用户`)
  if (value.deptIds.length)
    names.push(`${value.deptIds.length} 个部门${value.includeChildren ? '（含下级）' : ''}`)
  if (value.roleIds.length)
    names.push(`${value.roleIds.length} 个角色`)
  if (value.postIds.length)
    names.push(`${value.postIds.length} 个岗位`)
  return names.length ? `${names.join('、')}${value.excludedIds.length ? `，排除 ${value.excludedIds.length} 人` : ''}` : '尚未选择接收范围'
})
/** 打开后独立编辑，取消不污染发布表单中的范围。 */
async function begin() {
  local.value = cloneAudience(model.value)
  open.value = true
  error.value = ''
  page.value = 1
  keyword.value = ''
  applied.value = ''
  try {
    const selectedIds = [...new Set([...local.value.userIds, ...local.value.excludedIds])]
    const [organization, selected] = await Promise.all([request<AudienceOptions>('/messages/audience/options'), selectedIds.length ? request<MessagePerson[]>('/messages/audience/selected', 'POST', selectedIds) : Promise.resolve([])])
    options.value = organization
    selected.forEach(person => people.set(person.id, person))
    await load()
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '接收范围加载失败'
  }
}
/** 查询在后端分页过滤，异步响应只保留最近一次。 */
async function load() {
  const current = ++revision
  loading.value = true
  try {
    const result = await request<PageResult<MessagePerson>>('/messages/audience/users', 'GET', undefined, { keyword: applied.value, page: page.value, size: size.value, ...(dept.value ? { deptId: dept.value } : {}) })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
    rows.value.forEach(person => people.set(person.id, person))
  }
  catch (failure) {
    if (current === revision)
      error.value = failure instanceof Error ? failure.message : '人员加载失败'
  }
  finally {
    if (current === revision)
      loading.value = false
  }
}
/** 选择与排除分别维护；最终合并去重和人数由服务端确认。 */
function select(id: number, checked: boolean) {
  const values = local.value[target.value]
  local.value[target.value] = checked ? [...new Set([...values, id])] : values.filter(value => value !== id)
}
/** 确认后复制接收规则，取消对话框不会修改原表单。 */
function save() {
  model.value = cloneAudience(local.value)
  open.value = false
}
/** 人员查询从第一页开始，关键字交给后端处理。 */
function search() {
  page.value = 1
  applied.value = keyword.value.trim()
  void load()
}
watch(dept, search)
</script>

<template>
  <div class="flex flex-wrap items-center gap-3">
    <Button type="button" variant="outline" :disabled="props.disabled" @click="begin">
      <Users class="size-4" />选择接收范围
    </Button><span class="text-sm text-muted-foreground">{{ summary }}</span>
  </div>
  <Modal v-model:open="open" title="选择接收范围" :width="1000" :z-index="60" ok-text="使用此范围" cancel-text="取消" :destroy-on-close="true" @ok="save">
    <div class="my-4 grid gap-5 lg:grid-cols-[minmax(0,1fr)_250px]">
      <div class="min-w-0 space-y-4">
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label class="space-y-2"><span class="block text-xs font-medium">指定部门</span><TreeSelect v-model:value="local.deptIds" class="w-full" :tree-data="tree" multiple :tree-checkable="false" placeholder="选择部门" allow-clear :max-tag-count="2" :dropdown-style="{ maxHeight: '320px' }" /><ACheckbox v-model:checked="local.includeChildren" class="!mt-2 text-xs">包含下级部门</ACheckbox></label>
          <label class="space-y-2"><span class="block text-xs font-medium">指定角色</span><ASelect v-model:value="local.roleIds" class="w-full" mode="multiple" :options="options.roles.map(item => ({ value: item.id, label: item.name }))" placeholder="选择角色" :max-tag-count="2" allow-clear /></label>
          <label class="space-y-2"><span class="block text-xs font-medium">指定岗位</span><ASelect v-model:value="local.postIds" class="w-full" mode="multiple" :options="options.posts.map(item => ({ value: item.id, label: item.name }))" placeholder="选择岗位" :max-tag-count="2" allow-clear /></label>
          <div v-if="auth.can('message:send:all')" class="flex items-center justify-between rounded border bg-muted/20 px-3">
            <span class="text-sm">发送给全体用户</span><ASwitch v-model:checked="local.all" aria-label="发送给全体用户" />
          </div>
        </div>
        <div class="border-t pt-4">
          <div class="mb-3 flex gap-2">
            <Button type="button" size="sm" :variant="target === 'userIds' ? 'secondary' : 'ghost'" @click="target = 'userIds'">
              指定用户
            </Button><Button type="button" size="sm" :variant="target === 'excludedIds' ? 'secondary' : 'ghost'" @click="target = 'excludedIds'">
              排除用户
            </Button>
          </div>
          <form class="mb-3 flex flex-wrap gap-2" @submit.prevent="search">
            <AInput v-model:value="keyword" class="!w-48" placeholder="姓名或账号" allow-clear /><ASelect v-model:value="dept" class="!w-40" :options="options.departments.map(item => ({ value: item.id, label: item.name }))" placeholder="全部部门" allow-clear /><Button type="submit" size="sm" :disabled="loading">
              <Search class="size-3.5" />查询
            </Button>
          </form>
          <div class="overflow-auto rounded border">
            <table class="w-full text-left text-sm">
              <thead class="bg-muted/40">
                <tr>
                  <th class="w-10 p-2" /><th class="p-2 font-medium">
                    姓名 / 账号
                  </th><th class="p-2 font-medium">
                    部门
                  </th>
                </tr>
              </thead><tbody>
                <tr v-for="person in rows" :key="person.id" class="border-t hover:bg-muted/25">
                  <td class="p-2">
                    <ACheckbox :checked="local[target].includes(person.id)" :aria-label="`选择${person.nickname || person.username}`" @change="select(person.id, $event.target.checked)" />
                  </td><td class="p-2">
                    {{ person.nickname }}<span class="ml-2 text-xs text-muted-foreground">{{ person.username }}</span>
                  </td><td class="p-2 text-muted-foreground">
                    {{ person.deptName || '未分配部门' }}
                  </td>
                </tr><tr v-if="!rows.length">
                  <td colspan="3" class="p-8 text-center text-muted-foreground">
                    {{ loading ? '正在查询…' : '没有符合条件的用户' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <ListPagination v-model:size="size" :page="page" :total="total" :busy="loading" @change="page = $event; load()" />
        </div>
      </div>
      <aside class="min-w-0 border-t pt-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="font-medium">
            {{ target === 'userIds' ? '已选用户' : '排除用户' }} <span class="text-primary">{{ local[target].length }}</span>
          </h3><Button type="button" variant="ghost" size="sm" @click="local[target] = []">
            清空
          </Button>
        </div><div class="max-h-96 space-y-1 overflow-y-auto">
          <div v-for="id in local[target]" :key="id" class="flex items-center gap-2 rounded bg-muted/35 px-2 py-1.5">
            <span class="min-w-0 flex-1 truncate text-sm">{{ people.get(id)?.nickname || people.get(id)?.username || `编号 ${id}（待核验）` }}</span><Button type="button" variant="ghost" size="icon" class="size-6" :aria-label="`移除${people.get(id)?.nickname || id}`" @click="select(id, false)">
              <X class="size-3" />
            </Button>
          </div><p v-if="!local[target].length" class="py-8 text-center text-xs text-muted-foreground">
            从左侧选择人员
          </p>
        </div><p class="mt-5 text-xs leading-6 text-muted-foreground">
          用户、部门、角色、岗位按并集合并，排除用户最后生效。发布前将核验实际接收人数。
        </p>
      </aside>
    </div><p v-if="error" role="alert" class="text-sm text-destructive">
      {{ error }}
    </p>
  </Modal>
</template>
