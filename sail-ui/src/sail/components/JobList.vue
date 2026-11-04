<script setup lang="ts">
import { Plus } from '@lucide/vue'
import { Modal } from 'ant-design-vue'
import { computed, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'

import type { JobDefinition, JobHandler } from '@/sail/jobs'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import FormSelect from '@/sail/components/FormSelect.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { canRunJob, jobDefaults, jobPayload, jobStates, jobTime } from '@/sail/jobs'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ records: [jobId: number] }>()
const auth = useAuthStore()
const statuses = ['ENABLED', 'PAUSED', 'ARCHIVED'].map(value => ({ value, label: jobStates[value]! }))
const columns = [{ key: 'name', label: '任务 / 分组' }, { key: 'handler', label: '处理器' }, { key: 'schedule', label: '调度规则' }, { key: 'status', label: '状态' }, { key: 'sync', label: '调度同步' }, { key: 'nextFireAt', label: '下次执行' }, { key: 'lastStartedAt', label: '上次执行' }, { key: 'lastStatus', label: '最近结果' }]
const table = useListView('system-jobs', columns)
const rows = ref<JobDefinition[]>([])
const handlers = ref<JobHandler[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const applied = ref<Record<string, string>>({})
const error = useMessageError()
const busy = ref(false)
const writing = ref(false)
const editOpen = ref(false)
const editing = ref<number>()
const form = reactive(jobDefaults())
const formError = useMessageError()
const preview = ref<number[]>([])
const previewBusy = ref(false)
const selectedHandler = computed(() => handlers.value.find(handler => handler.code === form.handler))
let poll: ReturnType<typeof setInterval> | undefined
let revision = 0
let previewRevision = 0

async function load(silent = false) {
  const current = ++revision
  if (!silent)
    busy.value = true
  try {
    const result = await request<PageResult<JobDefinition>>('/system/jobs', 'GET', undefined, { ...applied.value, page: page.value, size: size.value })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
    error.value = ''
  }
  catch (failure) {
    if (current === revision)
      error.value = errorMessage(failure, '任务读取失败')
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
async function loadHandlers() {
  try {
    handlers.value = await request<JobHandler[]>('/system/jobs/handlers')
  }
  catch (failure) { error.value = errorMessage(failure, '处理器读取失败') }
}
function search() {
  applied.value = { keyword: keyword.value.trim(), status: status.value }
  page.value = 1
  void load()
}
function reset() {
  keyword.value = ''
  status.value = ''
  search()
}
function edit(row?: JobDefinition) {
  editing.value = row?.id
  Object.assign(form, jobDefaults(), row ? jobPayload(row) : { handler: handlers.value[0]?.code ?? '' })
  formError.value = ''
  preview.value = []
  editOpen.value = true
}
watch(form, () => {
  preview.value = []
  previewRevision++
})
async function previewSchedule() {
  const current = ++previewRevision
  previewBusy.value = true
  formError.value = ''
  try {
    const dates = await request<number[]>('/system/jobs/preview', 'POST', jobPayload(form) as unknown as Record<string, unknown>)
    if (current === previewRevision)
      preview.value = dates
  }
  catch (failure) { formError.value = errorMessage(failure, '规则校验失败') }
  finally { previewBusy.value = false }
}
async function save() {
  if (writing.value)
    return
  writing.value = true
  formError.value = ''
  try {
    await request(editing.value ? `/system/jobs/${editing.value}` : '/system/jobs', editing.value ? 'PUT' : 'POST', jobPayload(form) as unknown as Record<string, unknown>)
    editOpen.value = false
    feedback.success(editing.value ? '配置已保存' : '任务已创建')
    await load()
  }
  catch (failure) { formError.value = errorMessage(failure, '保存失败') }
  finally { writing.value = false }
}
function control(row: JobDefinition, next: string) {
  Modal.confirm({ title: `${jobStates[next]}“${row.name}”？`, content: next === 'ENABLED' ? '调度同步后按规则执行。' : next === 'ARCHIVED' ? '归档后不可重新启用，已有执行记录按保留策略保存。' : '暂停后取消尚未开始的排队执行，运行中的任务会继续完成。', okText: '确认', cancelText: '取消', onOk: () => action(`/system/jobs/${row.id}/status`, 'PUT', { status: next, version: row.version }) })
}
async function action(path: string, method: string, body: Record<string, unknown>) {
  if (writing.value)
    return
  writing.value = true
  try {
    await request(path, method, body)
    feedback.success(method === 'POST' ? '已加入执行队列' : '状态已更新')
    await load()
  }
  catch (failure) {
    error.value = errorMessage(failure, '操作失败')
    throw failure
  }
  finally { writing.value = false }
}
function run(row: JobDefinition) {
  Modal.confirm({ title: `立即执行“${row.name}”？`, content: '将使用当前已保存的参数执行一次，同一任务已有运行时不能重复触发。', okText: '执行一次', cancelText: '取消', onOk: () => action(`/system/jobs/${row.id}/run`, 'POST', { version: row.version }) })
}
function deactivate() {
  clearInterval(poll)
  revision++
  busy.value = false
}
onActivated(() => {
  void load()
  void loadHandlers()
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
    <AdvancedTable v-model:show-query="table.showQuery.value" v-model:hidden-columns="table.hiddenColumns.value" v-model:column-order="table.columnOrder.value" v-model:left-pins="table.leftPins.value" v-model:right-pins="table.rightPins.value" v-model:density="table.density.value" :columns="columns" title="任务列表" :total="total" :loading="busy" @refresh="load()">
      <template #query>
        <QueryBar v-model:keyword="keyword" v-model:status="status" label="任务检索" placeholder="名称、分组或处理器" :statuses="statuses" :busy="busy" @search="search" @reset="reset" />
      </template>
      <template #actions>
        <Button v-if="auth.can('system:job:create')" size="sm" :disabled="!handlers.length" @click="edit()">
          <Plus class="size-4" />新增任务
        </Button>
      </template>

      <div class="overflow-auto">
        <table class="w-full min-w-[1200px] text-left text-sm">
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
              <td v-for="column in table.visibleColumns.value" :key="column.key" :data-column="column.key" class="max-w-64 px-4 py-3">
                <template v-if="column.key === 'name'">
                  <p class="truncate font-medium" :title="row.name">
                    {{ row.name }}
                  </p><p class="mt-1 text-xs text-muted-foreground">
                    {{ row.groupName }} · #{{ row.id }}
                  </p>
                </template>
                <template v-else-if="column.key === 'handler'">
                  <p>{{ handlers.find(h => h.code === row.handler)?.name ?? row.handler }}</p><p class="text-xs text-muted-foreground">
                    {{ row.handler }}
                  </p>
                </template>
                <template v-else-if="column.key === 'schedule'">
                  <p class="font-mono text-xs">
                    {{ row.scheduleType === 'CRON' ? row.cron : `每 ${row.intervalSeconds} 秒（固定频率）` }}
                  </p><p class="mt-1 text-xs text-muted-foreground">
                    {{ row.zoneId }} · {{ row.misfirePolicy === 'SKIP' ? '错过跳过' : '恢复补一次' }}
                  </p>
                </template>
                <template v-else-if="column.key === 'status'">
                  <span :class="row.status === 'ENABLED' ? 'text-primary' : 'text-muted-foreground'">{{ jobStates[row.status] }}</span><p v-if="row.activeRunId" class="mt-1 text-xs text-amber-600">
                    执行 #{{ row.activeRunId }} 占用中
                  </p>
                </template>
                <template v-else-if="column.key === 'sync'">
                  <span :class="row.syncStatus === 'ERROR' ? 'text-destructive' : 'text-muted-foreground'">{{ jobStates[row.syncStatus] }}</span><p v-if="row.syncError" class="mt-1 text-xs text-destructive">
                    {{ row.syncError }}
                  </p>
                </template>
                <template v-else-if="column.key === 'lastStatus'">
                  <span>{{ jobStates[row.lastStatus ?? ''] || '尚未执行' }}</span>
                </template>
                <template v-else>
                  {{ jobTime(row[column.key as 'nextFireAt' | 'lastStartedAt']) }}
                </template>
              </td>
              <td data-column="actions" class="px-4 py-3">
                <div class="flex flex-wrap gap-x-3 gap-y-2 whitespace-nowrap">
                  <Button v-if="auth.can('system:job:update') && row.status !== 'ARCHIVED'" variant="link" size="sm" class="h-auto p-0" :disabled="writing" @click="edit(row)">
                    编辑
                  </Button>
                  <Button v-if="auth.can('system:job:control') && row.status !== 'ARCHIVED'" variant="link" size="sm" class="h-auto p-0" :disabled="writing" @click="control(row, row.status === 'ENABLED' ? 'PAUSED' : 'ENABLED')">
                    {{ row.status === 'ENABLED' ? '暂停' : '启用' }}
                  </Button>
                  <Button v-if="auth.can('system:job:run')" variant="link" size="sm" class="h-auto p-0" :disabled="writing || !canRunJob(row)" @click="run(row)">
                    立即执行
                  </Button>
                  <Button v-if="auth.can('system:job:log:query')" variant="link" size="sm" class="h-auto p-0" @click="emit('records', row.id)">
                    记录
                  </Button>
                  <Button v-if="auth.can('system:job:control') && row.status !== 'ARCHIVED'" variant="link" size="sm" class="h-auto p-0 text-muted-foreground" :disabled="writing || !!row.activeRunId" @click="control(row, 'ARCHIVED')">
                    归档
                  </Button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td :colspan="columns.length + 1" class="py-14 text-center text-muted-foreground">
                {{ busy ? '正在加载任务…' : '暂无任务' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #pagination>
        <ListPagination v-model:size="size" :page="page" :total="total" :busy="busy" @change="page = $event; load()" />
      </template>
    </AdvancedTable>

    <Dialog v-model:open="editOpen">
      <DialogContent :aria-describedby="undefined" class="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader><DialogTitle>{{ editing ? '编辑定时任务' : '新增定时任务' }}</DialogTitle></DialogHeader>
        <SailForm class="space-y-4" @submit.prevent="save">
          <fieldset :disabled="writing" class="space-y-4">
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-2">
                <Label for="job-name">任务名称</Label><Input id="job-name" v-model="form.name" required :maxlength="100" />
              </div><div class="space-y-2">
                <Label for="job-group">任务分组</Label><Input id="job-group" v-model="form.groupName" required :maxlength="64" />
              </div>
            </div>
            <div class="space-y-2">
              <Label for="job-handler">处理器</Label><FormSelect id="job-handler" v-model="form.handler" required :options="handlers.map(h => ({ value: h.code, label: h.name }))" />
            </div>
            <div class="space-y-2">
              <Label for="job-parameters">参数（JSON）</Label><textarea id="job-parameters" v-model="form.parameters" required maxlength="4000" rows="3" class="w-full rounded-md border bg-background p-3 font-mono text-sm" />
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-2">
                <Label for="job-type">调度方式</Label><FormSelect id="job-type" v-model="form.scheduleType" :options="[{ value: 'CRON', label: 'Cron 表达式' }, { value: 'INTERVAL', label: '固定频率间隔' }]" />
              </div><div class="space-y-2">
                <Label for="job-zone">时区</Label><Input id="job-zone" v-model="form.zoneId" required placeholder="Asia/Shanghai" />
              </div>
            </div>
            <div v-if="form.scheduleType === 'CRON'" class="space-y-2">
              <Label for="job-cron">Quartz Cron</Label><Input id="job-cron" v-model="form.cron" required placeholder="0 0/5 * * * ?" />
            </div>
            <div v-else class="space-y-2">
              <Label for="job-interval">间隔（秒）</Label><Input id="job-interval" v-model.number="form.intervalSeconds" type="number" :min="10" :max="2592000" required />
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-2">
                <Label for="job-misfire">错过执行</Label><FormSelect id="job-misfire" v-model="form.misfirePolicy" :options="[{ value: 'SKIP', label: '跳过错过的时刻' }, { value: 'FIRE_ONCE', label: '恢复后补执行一次' }]" />
              </div><div class="space-y-2">
                <Label for="job-timeout">超时阈值（秒）</Label><Input id="job-timeout" v-model.number="form.timeoutSeconds" type="number" :min="5" :max="86400" required />
              </div>
            </div>
            <div class="grid gap-4 sm:grid-cols-3">
              <div class="space-y-2">
                <Label for="job-retries">自动重试次数</Label><Input id="job-retries" v-model.number="form.maxRetries" type="number" :min="0" :max="selectedHandler?.retryable ? 3 : 0" required />
              </div><div class="space-y-2">
                <Label for="job-delay">首次重试等待（秒）</Label><Input id="job-delay" v-model.number="form.retryDelaySeconds" type="number" :min="10" :max="3600" required />
              </div><div class="space-y-2">
                <Label for="job-retention">记录保留（天）</Label><Input id="job-retention" v-model.number="form.retentionDays" type="number" :min="1" :max="365" required />
              </div>
            </div>
          </fieldset>

          <div class="rounded-md border bg-muted/20 p-3">
            <Button type="button" variant="outline" size="sm" :disabled="previewBusy || writing" @click="previewSchedule">
              {{ previewBusy ? '校验中…' : '校验并预览未来 5 次' }}
            </Button><ol v-if="preview.length" class="mt-3 space-y-1 text-sm">
              <li v-for="(time, index) in preview" :key="time">
                {{ index + 1 }}. {{ jobTime(time, form.zoneId) }}（{{ form.zoneId }}）
              </li>
            </ol>
          </div>
          <div class="flex justify-end gap-2">
            <Button type="button" variant="outline" :disabled="writing" @click="editOpen = false">
              取消
            </Button><Button type="submit" :disabled="writing">
              {{ writing ? '保存中…' : '保存任务' }}
            </Button>
          </div>
        </SailForm>
      </DialogContent>
    </Dialog>
  </section>
</template>
