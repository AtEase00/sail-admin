<script setup lang="ts">
import { Download, MoreHorizontal, Plus, RefreshCw } from '@lucide/vue'
import { Input as AInput, Select as ASelect, Drawer, Modal } from 'ant-design-vue'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { MessageReceipt, MessageTask } from '@/sail/message-center'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useMessageError } from '@/composables/use-message-error'
import { apiFetch, request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import MessageComposer from '@/sail/components/MessageComposer.vue'
import MessageContentView from '@/sail/components/MessageContentView.vue'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import { saveMessageBlob } from '@/sail/message-center'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionaries'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const dictionaries = useDictionaryStore()
const composerOpen = computed(() => auth.can('message:send') && route.query.compose === '1')
function compose(query: Record<string, string | number> = {}) {
  void router.push({ path: '/messages/history', query: { compose: '1', ...query } })
}
function closeComposer() {
  void router.push({ path: '/messages/history' })
}
watch(composerOpen, (open, previous) => {
  if (!open && previous && auth.can('message:query'))
    void load()
})
const columns = [{ key: 'title', label: '消息标题' }, { key: 'category', label: '分类' }, { key: 'priority', label: '重要程度' }, { key: 'status', label: '发布状态' }, { key: 'delivery', label: '投递 / 接收' }, { key: 'reading', label: '已读 / 已确认' }, { key: 'senderName', label: '发布人' }, { key: 'createdAt', label: '创建时间' }]
const { showQuery, hiddenColumns, columnOrder, leftPins, rightPins, density, visibleColumns } = useListView('message-tasks', columns)
const keyword = ref('')
const status = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const values = ref<Record<string, string>>({})
const applied = ref<Record<string, string>>({})
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<MessageTask[]>([])
const busy = ref(false)
const error = useMessageError()
let revision = 0
let polling: number | undefined
const fields = computed(() => [{ key: 'category', label: '消息分类', options: dictionaries.options('message_category') }, { key: 'priority', label: '重要程度', options: dictionaries.options('message_priority') }])
const detailOpen = ref(false)
const task = ref<MessageTask>()
const detailBusy = ref(false)
const detailError = useMessageError()
const receipts = ref<MessageReceipt[]>([])
const receiptKeyword = ref('')
const receiptStatus = ref('')
const receiptApplied = ref({ keyword: '', receiptStatus: '' })
const receiptPage = ref(1)
const receiptSize = ref(10)
const receiptTotal = ref(0)
const receiptBusy = ref(false)
let receiptRevision = 0
const receiptOptions = computed(() => [...dictionaries.options('message_delivery_status'), { value: 'READ', label: '已阅读' }, { value: 'UNREAD', label: '未阅读' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'UNCONFIRMED', label: '未确认' }])
/** 查询固定交给后端，翻页与自动刷新共用已应用的条件。 */
async function load() {
  if (!auth.can('message:query'))
    return
  const current = ++revision
  busy.value = true
  error.value = ''
  try {
    const result = await request<PageResult<MessageTask>>('/messages/tasks', 'GET', undefined, { ...applied.value, page: page.value, size: size.value })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
  }
  catch (failure) {
    if (current === revision)
      error.value = errorMessage(failure, '发布记录加载失败')
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
/** 固定本次查询条件，翻页及轮询始终使用同一条件。 */
function search() {
  applied.value = { keyword: keyword.value.trim(), status: status.value, ...values.value, ...(createdFrom.value ? { createdFrom: createdFrom.value } : {}), ...(createdTo.value ? { createdTo: createdTo.value } : {}) }
  page.value = 1
  void load()
}
/** 重置筛选并重新查询发布记录。 */
function reset() {
  keyword.value = ''
  status.value = ''
  values.value = {}
  createdFrom.value = ''
  createdTo.value = ''
  search()
}
/** 发布详情与个人收件详情不同，不触发任何阅读回执。 */
async function showDetail(id: number) {
  detailOpen.value = true
  detailBusy.value = true
  detailError.value = ''
  task.value = undefined
  receipts.value = []
  receiptPage.value = 1
  receiptKeyword.value = ''
  receiptStatus.value = ''
  receiptApplied.value = { keyword: '', receiptStatus: '' }
  try {
    task.value = await request<MessageTask>(`/messages/tasks/${id}`)
    if (auth.can('message:receipt:query'))
      await loadReceipts()
  }
  catch (failure) {
    detailError.value = errorMessage(failure, '发布详情加载失败')
  }
  finally {
    detailBusy.value = false
  }
}
/** 回执搜索和导出使用同一组已应用条件。 */
async function loadReceipts() {
  if (!task.value)
    return
  const current = ++receiptRevision
  receiptBusy.value = true
  try {
    const result = await request<PageResult<MessageReceipt>>(`/messages/tasks/${task.value.id}/receipts`, 'GET', undefined, { ...receiptApplied.value, page: receiptPage.value, size: receiptSize.value })
    if (current !== receiptRevision)
      return
    receipts.value = result.records
    receiptTotal.value = result.total
  }
  catch (failure) {
    if (current === receiptRevision)
      detailError.value = errorMessage(failure, '接收回执加载失败')
  }
  finally {
    if (current === receiptRevision)
      receiptBusy.value = false
  }
}
/** 回执查询条件独立于主列表，并由服务端分页过滤。 */
function searchReceipts() {
  receiptApplied.value = { keyword: receiptKeyword.value.trim(), receiptStatus: receiptStatus.value }
  receiptPage.value = 1
  void loadReceipts()
}
/** 导出沿用当前回执筛选，通过鉴权请求下载文件。 */
async function exportReceipts() {
  if (!task.value)
    return
  try {
    const blob = await apiFetch<Blob, 'blob'>(`/messages/tasks/${task.value.id}/receipts/export`, { responseType: 'blob', query: receiptApplied.value })
    saveMessageBlob(blob, `消息回执-${task.value.id}.csv`)
  }
  catch {
    detailError.value = '回执导出失败，请检查权限并重试'
  }
}
/** 危险操作明确展示对象和结果，实际状态及权限仍由后端核验。 */
function action(row: MessageTask, operation: string) {
  const labels: Record<string, string> = { cancel: '取消发布', withdraw: '撤回消息', retry: '重试失败消息', delete: '删除草稿' }
  Modal.confirm({ title: `${labels[operation]}？`, content: `消息：${row.title || '未命名草稿'}。${operation === 'withdraw' ? '撤回后接收人无法再访问正文和附件，阅读记录保留。' : operation === 'retry' ? '仅重新处理失败人员，成功人员不会重复收到。' : ''}`, okText: labels[operation], cancelText: '返回', okButtonProps: { danger: ['withdraw', 'delete'].includes(operation) }, onOk: async () => {
    try {
      await request(operation === 'delete' ? `/messages/tasks/${row.id}` : `/messages/tasks/${row.id}/${operation}`, operation === 'delete' ? 'DELETE' : 'POST')
      feedback.success('操作成功')
      await load()
    }
    catch (failure) {
      error.value = errorMessage(failure, '操作失败')
      throw failure
    }
  } })
}
/** 本人发布限制与后端归属校验保持一致。 */
function own(row: MessageTask) {
  return row.senderId === auth.identity?.user.id
}
/** 活跃任务才自动更新，离开页面或后台标签停止无意义轮询。 */
function startPolling() {
  window.clearInterval(polling)
  polling = window.setInterval(() => {
    if (!document.hidden && !busy.value && rows.value.some(row => ['QUEUED', 'SENDING', 'SCHEDULED'].includes(row.status)))
      void load()
  }, 5000)
}
onMounted(() => {
  void load()
  startPolling()
})
onActivated(startPolling)
onDeactivated(() => window.clearInterval(polling))
onBeforeUnmount(() => {
  revision++
  receiptRevision++
  window.clearInterval(polling)
})
</script>

<template>
  <MessageTheme>
    <AdvancedTable v-if="auth.can('message:query')" v-model:show-query="showQuery" v-model:hidden-columns="hiddenColumns" v-model:column-order="columnOrder" v-model:left-pins="leftPins" v-model:right-pins="rightPins" v-model:density="density" :columns="columns" title="消息发布记录" :total="total" :loading="busy" @refresh="load">
      <template #query>
        <QueryBar v-model:keyword="keyword" v-model:status="status" v-model:values="values" v-model:created-from="createdFrom" v-model:created-to="createdTo" dates label="消息标题" placeholder="输入消息标题" :fields="fields" :statuses="dictionaries.options('message_task_status')" :busy="busy" @search="search" @reset="reset" />
      </template>
      <template #actions>
        <Button v-if="auth.can('message:send')" size="sm" @click="compose()">
          <Plus class="size-4" />发布消息
        </Button>
      </template>

      <div class="overflow-auto">
        <table class="w-full min-w-[980px] text-left text-sm">
          <thead class="bg-muted/40">
            <tr>
              <th v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="px-4 py-3 font-medium whitespace-nowrap">
                {{ column.label }}
              </th><th data-column="actions" class="w-36 px-4 py-3 font-medium">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr v-for="row in rows" :key="row.id" class="border-t hover:bg-muted/20">
              <td v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="px-4 py-3">
                <button v-if="column.key === 'title'" class="block max-w-64 truncate text-left text-primary hover:underline" @click="showDetail(row.id)">
                  {{ row.title || '未命名草稿' }}
                </button><span v-else-if="['category', 'priority', 'status'].includes(column.key)" class="whitespace-nowrap" :class="{ 'text-destructive': column.key === 'status' && ['FAILED', 'PARTIAL_FAILED'].includes(row.status), 'text-primary': column.key === 'status' && row.status === 'SENT' }">{{ dictionaries.label(column.key === 'status' ? 'message_task_status' : `message_${column.key}`, row[column.key as 'category' | 'priority' | 'status']) }}</span><span v-else-if="column.key === 'delivery'" class="whitespace-nowrap">{{ row.statistics.delivered }} / {{ row.statistics.total }}<span v-if="row.statistics.failed" class="ml-1 text-xs text-destructive">失败 {{ row.statistics.failed }}</span></span><span v-else-if="column.key === 'reading'" class="whitespace-nowrap">{{ row.statistics.readCount }} / {{ row.requireConfirmation ? row.statistics.confirmed : '无需确认' }}</span><span v-else class="whitespace-nowrap text-muted-foreground">{{ row[column.key as 'senderName' | 'createdAt'] || '—' }}</span>
              </td><td data-column="actions" class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <Button size="sm" variant="link" class="h-7 px-0" @click="showDetail(row.id)">
                    详情
                  </Button><Button v-if="own(row) && auth.can('message:send') && ['DRAFT', 'CANCELLED'].includes(row.status)" size="sm" variant="link" class="h-7 px-0" @click="compose({ draft: row.id })">
                    编辑
                  </Button><DropdownMenu>
                    <DropdownMenuTrigger as-child>
                      <Button variant="ghost" size="icon" class="size-7" :aria-label="`更多操作：${row.title}`">
                        <MoreHorizontal class="size-4" />
                      </Button>
                    </DropdownMenuTrigger><DropdownMenuContent align="end">
                      <DropdownMenuItem v-if="own(row) && auth.can('message:send')" @select="compose({ copy: row.id })">
                        复制为新消息
                      </DropdownMenuItem><DropdownMenuItem v-if="auth.can('message:cancel') && ['QUEUED', 'SCHEDULED'].includes(row.status)" @select="action(row, 'cancel')">
                        取消发布
                      </DropdownMenuItem><DropdownMenuItem v-if="auth.can('message:retry') && ['FAILED', 'PARTIAL_FAILED'].includes(row.status)" @select="action(row, 'retry')">
                        重试失败人员
                      </DropdownMenuItem><DropdownMenuItem v-if="auth.can('message:withdraw') && ['SENDING', 'SENT', 'PARTIAL_FAILED', 'FAILED'].includes(row.status)" class="text-destructive" @select="action(row, 'withdraw')">
                        撤回消息
                      </DropdownMenuItem><DropdownMenuItem v-if="own(row) && auth.can('message:send') && ['DRAFT', 'CANCELLED'].includes(row.status)" class="text-destructive" @select="action(row, 'delete')">
                        删除草稿
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </td>
            </tr><tr v-if="!rows.length">
              <td :colspan="visibleColumns.length + 1" class="p-12 text-center text-muted-foreground">
                {{ busy ? '正在读取发布记录…' : error ? '记录暂不可用，请重试' : '暂无发布记录' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #pagination>
        <ListPagination v-model:size="size" :page="page" :total="total" :busy="busy" @change="page = $event; load()" />
      </template>
    </AdvancedTable>
    <Drawer v-model:open="detailOpen" title="发布详情" width="min(1000px,96vw)" :z-index="60" :mask-closable="false">
      <p v-if="detailBusy" class="py-10 text-center text-muted-foreground">
        正在读取发布详情…
      </p><div v-if="task" class="space-y-6">
        <div>
          <div class="mb-3 flex items-center justify-between gap-3">
            <h2 class="text-xl font-semibold break-words">
              {{ task.title || '未命名草稿' }}
            </h2><span class="shrink-0 rounded bg-muted px-2 py-1 text-xs">{{ dictionaries.label('message_task_status', task.status) }}</span>
          </div><p class="text-xs text-muted-foreground">
            创建于 {{ task.createdAt }} · {{ task.scheduledAt ? `计划发送 ${task.scheduledAt}` : '立即发送' }}{{ task.requireConfirmation ? ' · 需要接收人确认' : '' }}
          </p><p v-if="task.lastError" class="mt-3 text-sm text-destructive">
            {{ task.lastError }}
          </p>
        </div>
        <dl class="grid grid-cols-3 gap-y-4 rounded bg-muted/35 p-4 sm:grid-cols-6">
          <div v-for="stat in [{ key: 'total', label: '接收人数' }, { key: 'delivered', label: '已投递' }, { key: 'readCount', label: '已阅读' }, { key: 'confirmed', label: '已确认' }, { key: 'failed', label: '投递失败' }, { key: 'skipped', label: '已跳过' }]" :key="stat.key">
            <dt class="text-xs text-muted-foreground">
              {{ stat.label }}
            </dt><dd class="mt-1 text-xl font-semibold">
              {{ task.statistics[stat.key as keyof typeof task.statistics] }}
            </dd>
          </div>
        </dl>
        <MessageContentView :content="task.content" :task-id="task.status === 'WITHDRAWN' ? null : task.id" /><p v-if="task.attachments.length" class="text-sm text-muted-foreground">
          附件：{{ task.attachments.map(file => file.name).join('、') }}
        </p>
        <section v-if="auth.can('message:receipt:query')" class="border-t pt-5">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="font-semibold">
              接收人回执
            </h3><div class="flex gap-2">
              <Button variant="ghost" size="icon" aria-label="刷新回执" :disabled="receiptBusy" @click="showDetail(task.id)">
                <RefreshCw class="size-4" />
              </Button><Button v-if="auth.can('message:receipt:export')" variant="outline" size="sm" @click="exportReceipts">
                <Download class="size-4" />导出回执
              </Button>
            </div>
          </div><form class="mb-3 flex flex-wrap gap-2" @submit.prevent="searchReceipts">
            <AInput v-model:value="receiptKeyword" class="!w-44" placeholder="姓名或账号" allow-clear /><ASelect v-model:value="receiptStatus" class="!w-40" :options="receiptOptions" placeholder="全部回执状态" allow-clear /><Button type="submit" size="sm" :disabled="receiptBusy">
              查询
            </Button>
          </form><div class="overflow-auto rounded border">
            <table class="w-full min-w-[700px] text-left text-xs">
              <thead class="bg-muted/40">
                <tr>
                  <th class="p-3">
                    接收人
                  </th><th class="p-3">
                    部门快照
                  </th><th class="p-3">
                    投递状态
                  </th><th class="p-3">
                    首次阅读时间
                  </th><th class="p-3">
                    确认时间
                  </th><th class="p-3">
                    投递说明
                  </th>
                </tr>
              </thead><tbody>
                <tr v-for="receipt in receipts" :key="receipt.id" class="border-t">
                  <td class="p-3">
                    {{ receipt.nickname }}<span class="mt-1 block text-muted-foreground">{{ receipt.username }}</span>
                  </td><td class="p-3">
                    {{ receipt.deptName || '未分配部门' }}
                  </td><td class="p-3">
                    {{ dictionaries.label('message_delivery_status', receipt.status) }}
                  </td><td class="p-3 whitespace-nowrap">
                    {{ receipt.readAt || '尚未阅读' }}
                  </td><td class="p-3 whitespace-nowrap">
                    {{ receipt.confirmedAt || (task.requireConfirmation ? '尚未确认' : '无需确认') }}
                  </td><td class="max-w-40 p-3 break-words">
                    {{ receipt.errorMessage || '—' }}
                  </td>
                </tr><tr v-if="!receipts.length">
                  <td colspan="6" class="p-8 text-center text-muted-foreground">
                    {{ receiptBusy ? '正在读取…' : '暂无接收回执' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div><ListPagination v-model:size="receiptSize" :page="receiptPage" :total="receiptTotal" :busy="receiptBusy" @change="receiptPage = $event; loadReceipts()" />
        </section>
      </div>
    </Drawer>
    <Button v-if="!auth.can('message:query') && auth.can('message:send')" size="sm" @click="compose()">
      <Plus class="size-4" />发布消息
    </Button>
    <Drawer :open="composerOpen" title="发布消息" width="min(1360px,100vw)" :z-index="60" :mask-closable="false" :keyboard="false" @close="closeComposer">
      <MessageComposer v-if="composerOpen" />
    </Drawer>
  </MessageTheme>
</template>

<route lang="yaml">
meta:
  sail: true
  title: 发布记录
  auth: true
  permissions: [message:query, message:send]
</route>
