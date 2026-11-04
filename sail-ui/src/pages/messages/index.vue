<script setup lang="ts">
import { Archive, ArchiveRestore, CheckCheck, Inbox, Mail, Pin, Star } from '@lucide/vue'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { InboxMessage } from '@/sail/message-center'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage } from '@/lib/message'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import MessageDetail from '@/sail/components/MessageDetail.vue'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import { messageUnavailable } from '@/sail/message-center'
import { useDictionaryStore } from '@/stores/dictionaries'

const route = useRoute()
const router = useRouter()
const dictionaries = useDictionaryStore()
const folder = ref('all')
const keyword = ref('')
const status = ref('')
const values = ref<Record<string, string>>({})
const createdFrom = ref('')
const createdTo = ref('')
const applied = ref<Record<string, string>>({})
const rows = ref<InboxMessage[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selected = ref<number[]>([])
const busy = ref(false)
const writing = ref(false)
const error = useMessageError()
const showQuery = ref(true)
const detailOpen = ref(false)
const detailId = ref<number>()
let revision = 0
let active = true
const folders = [{ key: 'all', label: '全部消息', icon: Inbox }, { key: 'unread', label: '未读消息', icon: Mail }, { key: 'important', label: '重要消息', icon: Star }, { key: 'archived', label: '已归档', icon: Archive }]
const fields = computed(() => [{ key: 'category', label: '消息分类', options: dictionaries.options('message_category') }])
const allSelected = computed(() => rows.value.length > 0 && rows.value.every(row => selected.value.includes(row.id)))
/** 接收箱服务端分页查询，推送与翻页竞争时丢弃过期响应。 */
async function load() {
  if (!active)
    return
  const current = ++revision
  busy.value = true
  error.value = ''
  try {
    const result = await request<PageResult<InboxMessage>>('/messages/mine', 'GET', undefined, { ...applied.value, folder: folder.value, page: page.value, size: size.value })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
    selected.value = []
  }
  catch (failure) {
    if (current === revision)
      error.value = errorMessage(failure, '收件箱加载失败')
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
/** 提交收件箱筛选，重新查询第一页真实数据。 */
function search() {
  applied.value = { keyword: keyword.value.trim(), status: status.value, ...values.value, ...(createdFrom.value ? { createdFrom: createdFrom.value } : {}), ...(createdTo.value ? { createdTo: createdTo.value } : {}) }
  page.value = 1
  void load()
}
/** 清空收件箱查询字段并重新加载。 */
function reset() {
  keyword.value = ''
  status.value = ''
  values.value = {}
  createdFrom.value = ''
  createdTo.value = ''
  search()
}
/** 批量操作只保留用户明确选择的记录编号。 */
function select(id: number, checked: boolean) {
  selected.value = checked ? [...new Set([...selected.value, id])] : selected.value.filter(value => value !== id)
}
/** 打开个人消息详情，由后端记录首次阅读时间。 */
function openMessage(id: number) {
  detailId.value = id
  detailOpen.value = true
}
/** 已读和归档批量动作仅提交当前明确选择的消息编号。 */
async function change(action: 'read' | 'read-all' | 'archive') {
  if (writing.value || (action !== 'read-all' && !selected.value.length))
    return
  writing.value = true
  error.value = ''
  try {
    await request(action === 'archive' ? '/messages/archive' : `/messages/${action}`, 'PUT', action === 'read-all' ? undefined : action === 'archive' ? { ids: selected.value, archived: folder.value !== 'archived' } : { ids: selected.value })
    window.dispatchEvent(new Event('sail:messages-read'))
    await load()
  }
  catch (failure) {
    error.value = errorMessage(failure, '消息操作失败')
  }
  finally {
    writing.value = false
  }
}
/** 提醒入口定位单条消息后移除一次性查询参数。 */
function fromLink() {
  if (route.path === '/messages' && Number(route.query.message) > 0) {
    openMessage(Number(route.query.message))
    void router.replace({ path: '/messages', query: {} })
  }
}
watch(() => route.fullPath, fromLink)
watch(folder, () => {
  page.value = 1
  void load()
})
onMounted(() => {
  void load()
  fromLink()
  window.addEventListener('sail:messages-changed', load)
})
onActivated(() => {
  active = true
  void load()
  fromLink()
})
onDeactivated(() => {
  active = false
  revision++
})
onBeforeUnmount(() => {
  revision++
  window.removeEventListener('sail:messages-changed', load)
})
</script>

<template>
  <MessageTheme>
    <section class="space-y-4">
      <nav class="flex flex-wrap gap-1 rounded-lg border bg-background p-1.5" aria-label="消息文件夹">
        <Button v-for="item in folders" :key="item.key" :variant="folder === item.key ? 'secondary' : 'ghost'" class="px-4" :class="folder === item.key ? 'bg-primary/10 text-primary' : 'text-muted-foreground'" :aria-current="folder === item.key ? 'page' : undefined" @click="folder = item.key">
          <component :is="item.icon" class="size-4" />{{ item.label }}
        </Button>
      </nav>
      <AdvancedTable v-model:show-query="showQuery" title="我的消息" :total="total" :loading="busy || writing" @refresh="load">
        <template #query>
          <QueryBar v-model:keyword="keyword" v-model:status="status" v-model:values="values" v-model:created-from="createdFrom" v-model:created-to="createdTo" dates label="消息标题" :fields="fields" :statuses="[{ value: 'false', label: '未读' }, { value: 'true', label: '已读' }]" :busy="busy || writing" @search="search" @reset="reset" />
        </template>
        <template #actions>
          <Checkbox :model-value="allSelected" aria-label="选择本页全部消息" :disabled="busy || !rows.length" @update:model-value="selected = $event === true ? rows.map(row => row.id) : []" /><Button size="sm" variant="outline" :disabled="writing || !selected.length" @click="change('read')">
            <CheckCheck class="size-4" />标为已读
          </Button><Button size="sm" variant="outline" :disabled="writing || !selected.length" @click="change('archive')">
            <component :is="folder === 'archived' ? ArchiveRestore : Archive" class="size-4" />{{ folder === 'archived' ? '恢复消息' : '归档消息' }}
          </Button><Button size="sm" variant="ghost" :disabled="writing" @click="change('read-all')">
            全部已读
          </Button><span v-if="selected.length" class="text-xs text-muted-foreground">已选 {{ selected.length }} 条</span>
        </template>

        <div class="divide-y">
          <article v-for="message in rows" :key="message.id" class="group flex items-start gap-3 px-3 py-4 hover:bg-muted/20 sm:px-4" data-message-row>
            <Checkbox class="mt-2" :model-value="selected.includes(message.id)" :aria-label="`选择消息${message.title}`" :disabled="writing" @update:model-value="select(message.id, $event === true)" /><span class="relative mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-lg" :class="message.readFlag ? 'bg-muted text-muted-foreground' : 'bg-primary/10 text-primary'"><Mail class="size-5" /><span v-if="!message.readFlag && !messageUnavailable(message)" class="absolute top-0 right-0 size-2 rounded-full bg-primary ring-2 ring-background" /></span><button class="min-w-0 flex-1 text-left outline-offset-4 focus-visible:outline-2 focus-visible:outline-ring" @click="openMessage(message.id)">
              <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                <Pin v-if="message.pinned" class="size-3.5 text-primary" /><h2 class="truncate text-sm" :class="message.readFlag ? 'font-normal' : 'font-semibold'">
                  {{ message.title }}
                </h2><span v-if="message.priority && message.priority !== 'NORMAL'" class="rounded bg-primary/8 px-1.5 py-0.5 text-xs text-primary">{{ dictionaries.label('message_priority', message.priority) }}</span><span v-if="messageUnavailable(message)" class="text-xs text-muted-foreground">{{ message.taskStatus === 'WITHDRAWN' ? '已撤回' : '已过期' }}</span><span v-else-if="message.requireConfirmation" class="text-xs" :class="message.confirmedAt ? 'text-muted-foreground' : 'text-primary'">{{ message.confirmedAt ? '已确认' : '待确认' }}</span>
              </div><p class="mt-1.5 truncate text-sm text-muted-foreground">
                {{ messageUnavailable(message) ? '消息内容已不可访问' : message.summary || '点击查看消息详情' }}
              </p><p class="mt-2 flex flex-wrap gap-x-3 text-xs text-muted-foreground">
                <span>{{ message.senderName || '系统' }}</span><span v-if="message.category">{{ dictionaries.label('message_category', message.category) }}</span><span>{{ message.createdAt }}</span>
              </p>
            </button>
          </article><div v-if="!rows.length" class="flex flex-col items-center gap-3 p-14 text-center text-muted-foreground">
            <Inbox class="size-9 stroke-1" /><p class="text-sm">
              {{ busy ? '正在读取消息…' : error ? '暂时无法读取消息，请刷新重试' : folder === 'unread' ? '所有消息都已读完' : folder === 'archived' ? '暂无归档消息' : '暂无符合条件的消息' }}
            </p>
          </div>
        </div>
        <template #pagination>
          <ListPagination v-model:size="size" :page="page" :total="total" :busy="busy" @change="page = $event; load()" />
        </template>
      </AdvancedTable><MessageDetail :id="detailId" v-model:open="detailOpen" @changed="load" />
    </section>
  </MessageTheme>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  title: 我的消息
</route>
