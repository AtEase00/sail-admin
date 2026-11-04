<script setup lang="ts">
import { Modal } from 'ant-design-vue'
import { onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type { JobExecution } from '@/sail/jobs'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import { jobSources, jobStates, jobTime } from '@/sail/jobs'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const columns = [{ key: 'jobName', label: '任务 / 执行编号' }, { key: 'source', label: '触发来源' }, { key: 'status', label: '执行状态' }, { key: 'startedAt', label: '开始时间' }, { key: 'finishedAt', label: '结束时间' }, { key: 'durationMs', label: '耗时' }, { key: 'counts', label: '成功 / 失败' }, { key: 'summary', label: '结果摘要' }]
const table = useListView('job-executions', columns)
const rows = ref<JobExecution[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref('')
const filters = ref<Record<string, string>>({})
const fields = [
  { key: 'jobId', label: '任务编号', numeric: true, placeholder: '全部任务' },
  { key: 'source', label: '触发来源', options: Object.entries(jobSources).map(([value, label]) => ({ value, label })) },
]
const createdFrom = ref('')
const createdTo = ref('')
const applied = ref<Record<string, string>>({})
const busy = ref(false)
const writing = ref(false)
const error = useMessageError()
const detail = ref<JobExecution>()
const detailOpen = ref(false)
const detailError = useMessageError()
const reason = ref('')
let revision = 0
let detailRevision = 0
let active = false
let poll: ReturnType<typeof setInterval> | undefined
const statuses = ['QUEUED', 'RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'TIMED_OUT', 'UNKNOWN', 'SKIPPED'].map(value => ({ value, label: jobStates[value]! }))
async function load(silent = false) {
  const current = ++revision
  if (!silent)
    busy.value = true
  try {
    const result = await request<PageResult<JobExecution>>('/system/jobs/executions', 'GET', undefined, { ...applied.value, page: page.value, size: size.value })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
    error.value = ''
  }
  catch (failure) {
    if (current === revision)
      error.value = errorMessage(failure, '执行记录读取失败')
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
function search() {
  applied.value = { keyword: keyword.value.trim(), status: status.value, ...filters.value, ...(createdFrom.value ? { createdFrom: createdFrom.value } : {}), ...(createdTo.value ? { createdTo: createdTo.value } : {}) }
  page.value = 1
  void load()
}
function reset() {
  keyword.value = ''
  status.value = ''
  filters.value = {}
  createdFrom.value = ''
  createdTo.value = ''
  search()
}
async function show(id: number) {
  const current = ++detailRevision
  detailError.value = ''
  reason.value = ''
  detail.value = undefined
  detailOpen.value = true
  try {
    const result = await request<JobExecution>(`/system/jobs/executions/${id}`)
    if (current === detailRevision)
      detail.value = result
  }
  catch (failure) {
    if (current === detailRevision)
      detailError.value = errorMessage(failure, '详情读取失败')
  }
}
function retry(row: JobExecution) {
  Modal.confirm({ title: `重试执行 #${row.id}？`, content: '使用该任务的当前配置，仅允许已结束、配置版本未变且声明幂等的失败执行。将生成关联的新记录。', okText: '重试', cancelText: '取消', onOk: async () => {
    writing.value = true
    try {
      await request(`/system/jobs/executions/${row.id}/retry`, 'POST', { version: row.jobVersion })
      feedback.success('重试已排队')
      await load()
    }
    catch (failure) {
      error.value = errorMessage(failure, '重试失败')
      throw failure
    }
    finally { writing.value = false }
  } })
}
function resolveUnknown() {
  if (!detail.value || !reason.value.trim())
    return
  const row = detail.value
  const resolution = reason.value.trim()
  Modal.confirm({ title: '确认原执行进程已经停止？', content: '此操作不会终止任何进程。只有完成进程核查及业务结果对账后才能解除阻塞，否则可能重复执行业务。结果仍保留为“未知”。', okText: '已核查，解除阻塞', cancelText: '返回核查', okButtonProps: { danger: true }, onOk: async () => {
    writing.value = true
    try {
      await request(`/system/jobs/executions/${row.id}/resolve`, 'POST', { reason: resolution })
      feedback.success('已解除阻塞')
      await show(row.id)
      await load()
    }
    catch (failure) {
      detailError.value = errorMessage(failure, '处置失败')
      throw failure
    }
    finally { writing.value = false }
  } })
}
function deactivate() {
  active = false
  clearInterval(poll)
  revision++
  detailRevision++
  busy.value = false
}
watch(() => route.query.jobId, () => {
  if (!active)
    return
  filters.value.jobId = typeof route.query.jobId === 'string' ? route.query.jobId : ''
  search()
})
onActivated(() => {
  active = true
  filters.value.jobId = typeof route.query.jobId === 'string' ? route.query.jobId : ''
  search()
  poll = setInterval(() => {
    if (!busy.value && !writing.value && !document.hidden)
      void load(true)
  }, 5000)
})
onDeactivated(deactivate)
onBeforeUnmount(deactivate)
</script>

<template>
  <section class="space-y-4">
    <AdvancedTable v-model:show-query="table.showQuery.value" v-model:hidden-columns="table.hiddenColumns.value" v-model:column-order="table.columnOrder.value" v-model:left-pins="table.leftPins.value" v-model:right-pins="table.rightPins.value" v-model:density="table.density.value" :columns="columns" title="运行历史" :total="total" :loading="busy" @refresh="load()">
      <template #query>
        <QueryBar v-model:keyword="keyword" v-model:status="status" v-model:values="filters" v-model:created-from="createdFrom" v-model:created-to="createdTo" :fields="fields" dates label="记录检索" placeholder="任务名称或追踪号" :statuses="statuses" :busy="busy" @search="search" @reset="reset" />
      </template>

      <div class="overflow-auto">
        <table class="w-full min-w-[1100px] text-left text-sm">
          <thead class="bg-muted/40">
            <tr>
              <th v-for="column in table.visibleColumns.value" :key="column.key" :data-column="column.key" class="px-4 py-3 whitespace-nowrap">
                {{ column.label }}
              </th><th data-column="actions" class="px-4 py-3">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr v-for="row in rows" :key="row.id" class="border-t hover:bg-muted/20">
              <td v-for="column in table.visibleColumns.value" :key="column.key" :data-column="column.key" class="max-w-72 px-4 py-3">
                <template v-if="column.key === 'jobName'">
                  <p class="truncate font-medium">
                    {{ row.jobName }}
                  </p><p class="mt-1 text-xs text-muted-foreground">
                    执行 #{{ row.id }} · 任务 #{{ row.jobId }}
                  </p>
                </template>
                <template v-else-if="column.key === 'source'">
                  {{ jobSources[row.source] }}<p class="mt-1 text-xs text-muted-foreground">
                    {{ row.actorName || '系统调度' }}{{ row.attempt ? ` · 第 ${row.attempt} 次重试` : '' }}
                  </p>
                </template>
                <template v-else-if="column.key === 'status'">
                  <span :class="['FAILED', 'PARTIAL', 'TIMED_OUT', 'UNKNOWN'].includes(row.status) ? 'text-destructive' : row.status === 'SUCCESS' ? 'text-primary' : 'text-muted-foreground'">{{ jobStates[row.status] }}</span><p v-if="row.status === 'TIMED_OUT'" class="text-xs text-muted-foreground">
                    {{ row.finishedAt ? '处理器已返回' : '仍占用任务' }}
                  </p>
                </template>
                <template v-else-if="column.key === 'startedAt' || column.key === 'finishedAt'">
                  {{ jobTime(row[column.key]) }}
                </template>
                <template v-else-if="column.key === 'durationMs'">
                  {{ row.durationMs == null ? '—' : `${row.durationMs} ms` }}
                </template>
                <template v-else-if="column.key === 'counts'">
                  {{ row.succeeded }} / {{ row.failed }}
                </template>
                <template v-else>
                  <p class="line-clamp-2" :title="row.summary ?? ''">
                    {{ row.summary || '—' }}
                  </p>
                </template>
              </td><td data-column="actions" class="px-4 py-3">
                <div class="flex gap-3 whitespace-nowrap">
                  <Button variant="link" size="sm" class="h-auto p-0" @click="show(row.id)">
                    详情
                  </Button><Button v-if="auth.can('system:job:run') && ['FAILED', 'PARTIAL'].includes(row.status) && row.finishedAt" variant="link" size="sm" class="h-auto p-0" :disabled="writing" @click="retry(row)">
                    重试
                  </Button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td :colspan="columns.length + 1" class="py-14 text-center text-muted-foreground">
                {{ busy ? '正在加载记录…' : '暂无符合条件的执行记录' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #pagination>
        <ListPagination v-model:size="size" :page="page" :total="total" :busy="busy" @change="page = $event; load()" />
      </template>
    </AdvancedTable>

    <Dialog v-model:open="detailOpen">
      <DialogContent :aria-describedby="undefined" class="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader><DialogTitle>执行详情{{ detail ? ` #${detail.id}` : '' }}</DialogTitle></DialogHeader>

        <template v-if="detail">
          <dl class="grid grid-cols-[6rem_minmax(0,1fr)] gap-3 text-sm">
            <dt class="text-muted-foreground">
              任务
            </dt><dd>{{ detail.jobName }} · #{{ detail.jobId }} · 配置 v{{ detail.jobVersion }}</dd><dt class="text-muted-foreground">
              执行状态
            </dt><dd>{{ jobStates[detail.status] }}</dd><dt class="text-muted-foreground">
              触发来源
            </dt><dd>{{ jobSources[detail.source] }} · {{ detail.actorName || '系统调度' }}{{ detail.actorId ? `（#${detail.actorId}）` : '' }}</dd><dt class="text-muted-foreground">
              计划时间
            </dt><dd>{{ jobTime(detail.scheduledAt) }}</dd><dt class="text-muted-foreground">
              开始 / 结束
            </dt><dd>{{ jobTime(detail.startedAt) }} / {{ jobTime(detail.finishedAt) }}</dd><dt class="text-muted-foreground">
              最后心跳
            </dt><dd>{{ jobTime(detail.heartbeatAt) }}</dd><dt class="text-muted-foreground">
              实例
            </dt><dd class="break-all">
              {{ detail.instanceId || '尚未分配' }}
            </dd><dt class="text-muted-foreground">
              追踪号
            </dt><dd class="break-all font-mono text-xs">
              {{ detail.traceId }}
            </dd><dt class="text-muted-foreground">
              处理数量
            </dt><dd>成功 {{ detail.succeeded }} / 失败 {{ detail.failed }}</dd><dt class="text-muted-foreground">
              重试关系
            </dt><dd>
              <Button v-if="detail.parentRunId" variant="link" class="h-auto p-0" @click="show(detail.parentRunId)">
                原执行 #{{ detail.parentRunId }}
              </Button><span v-else>首次执行</span> · 重试次数 {{ detail.attempt }}
            </dd><dt class="text-muted-foreground">
              结果摘要
            </dt><dd class="break-words">
              {{ detail.summary || '—' }}
            </dd><template v-if="detail.resolution">
              <dt class="text-muted-foreground">
                处置说明
              </dt><dd>{{ detail.resolution }}</dd>
            </template>
          </dl>
          <details class="rounded-md border p-3">
            <summary class="cursor-pointer text-sm font-medium">
              查看配置快照
            </summary><pre class="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-all rounded bg-muted/40 p-3 text-xs">{{ JSON.stringify(JSON.parse(detail.configSnapshot), null, 2) }}</pre>
          </details>
          <div v-if="detail.status === 'UNKNOWN' && !detail.finishedAt && auth.can('system:job:control')" class="space-y-3 rounded-md border border-destructive/30 bg-destructive/5 p-4">
            <Label for="job-resolution">核查与处置说明</Label><textarea id="job-resolution" v-model="reason" maxlength="500" rows="3" class="w-full rounded border bg-background p-2 text-sm" /><Button variant="outline" :disabled="writing || !reason.trim()" @click="resolveUnknown">
              确认已停止并解除阻塞
            </Button>
          </div>
          <div class="flex justify-end">
            <Button variant="outline" :disabled="writing" @click="show(detail.id)">
              刷新详情
            </Button>
          </div>
        </template><p v-else-if="!detailError" class="py-8 text-center text-muted-foreground">
          正在加载详情…
        </p>
      </DialogContent>
    </Dialog>
  </section>
</template>
